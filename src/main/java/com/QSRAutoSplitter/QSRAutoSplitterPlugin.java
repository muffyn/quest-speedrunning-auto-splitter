package com.QSRAutoSplitter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static com.QSRAutoSplitter.QSRID.QUESTS_COMPLETE_COUNTER;
import static com.QSRAutoSplitter.QSRID.SPEEDRUN_ACTIVE_SIGNIFIER;

@Slf4j
@PluginDescriptor(
	name = "QSR Auto Splitter",
	description = "Sends split signals through LiveSplit server to automatically track splits for quest speedruns"
)
public class QSRAutoSplitterPlugin extends Plugin
{
	private static final Logger logger = LoggerFactory.getLogger(QSRAutoSplitterPlugin.class);

	// The number of quests completed. If this increases during a run, we've completed the quest.
	private int questsComplete;
	private int currTicks;

	// The variables to interact with livesplit
	PrintWriter writer;
	BufferedReader reader;

	@Inject
	private Client client;

	@Inject
	private QSRAutoSplitterConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	private boolean interpolate;

	// side panel
	private NavigationButton navButton;
	private QSRAutoSplitterPanel panel;

	// is the timer running?
	private boolean started = false;
	private boolean paused = false;

	private List<Pair<Integer, Integer>> itemList;
	private List<Pair<Integer, Integer>> varbList;
	private List<Pair<Integer, Integer>> varpList;

	@Provides
	QSRAutoSplitterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QSRAutoSplitterConfig.class);
	}

	@Override
	protected void startUp()
	{
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/qsr_auto_splitter_icon.png");
		panel = new QSRAutoSplitterPanel(client, writer, reader, config, this);
		navButton = NavigationButton.builder().tooltip("Quest Speedrunning Auto Splitter")
				.icon(icon).priority(6).panel(panel).build();
		clientToolbar.addNavigation(navButton);

		panel.startPanel();
	}

	@Override
	protected void shutDown()
	{
		sendMessage("pause");
		clientToolbar.removeNavigation(navButton);
		panel.disconnect();  // terminates active socket
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e) {
		sendMessage("pause");
	}

	private void sendMessage(String message) {

		if (writer != null) {
			writer.write(message + "\r\n");
			writer.flush();
		}
	}

	private String receiveMessage() {

		if (reader != null) {
			try {
				return reader.readLine();
			} catch (IOException e) {
				return "ERROR";
			}
		}
		return "ERROR";
	}

	private void setup(String configStr) {
		itemList = new ArrayList<>();
		varbList = new ArrayList<>();
		varpList = new ArrayList<>();

		String[] configList = configStr.split("\n");

		for (String line : configList) {
			String[] args = line.split(",");
			Pair<Integer, Integer> pair;
			try {
				int type = Integer.parseInt(args[1]);
				if (type == 0) {
					if (args.length < 4) { // default 1 item
						pair = new Pair<>(Integer.parseInt(args[2]), 1);
					} else {
						pair = new Pair<>(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
					}
					itemList.add(pair);
				} else if (type == 1) {
					pair = new Pair<>(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
					varbList.add(pair);
				} else if (type == 2) {
					pair = new Pair<>(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
					varpList.add(pair);
				} else {
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: could not parse line: " + line, null);
				}
			} catch (Exception e) {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: could not parse line: " + line, null);
			}
		}
	}
	@Subscribe
	public void onGameTick(GameTick event) {

		if (!started && isInSpeedrun()) {
			started = true;
			sendMessage("reset");
			sendMessage("initgametime"); //FIXME find better spot to init
			sendMessage("starttimer");

			questsComplete = client.getVarbitValue(QSRID.QUESTS_COMPLETE_COUNTER);
			int currQuest = client.getVarbitValue(QSRID.SPEEDRUN_QUEST_SIGNIFIER);
			String configStr = "";

			switch (currQuest) {
				case QSRID.CA:   configStr = config.caList();   break;
				case QSRID.DS:   configStr = config.dsList();   break;
				case QSRID.ETC:  configStr = config.etcList();  break;
				case QSRID.PAR:  configStr = config.parList();  break;
				case QSRID.VS:   configStr = config.vsList();   break;
				case QSRID.DSI:  configStr = config.dsiList();  break;
				case QSRID.BCS:  configStr = config.bcsList();  break;
				default:
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: run has not been implemented yet", null);
					configStr = "";
					break;
			}
			setup(configStr);
		} else if (started && client.getWidget(WidgetInfo.QUEST_COMPLETED_NAME_TEXT) != null) {
			completeRun();
			started = false;
		} else if (started && !isInSpeedrun()) {
			started = false;
			sendMessage("getcurrenttimerphase");
			switch (receiveMessage()) {
				case "Running":
					sendMessage("pause");
					break;
				case "NotRunning:":
				case "Paused":
				case "Ended":
				default:
					break;
			}
		}
		if ( client.getWidget(WidgetInfo.QUEST_COMPLETED_NAME_TEXT) != null) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: quest complete", null);
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		ScriptEvent scriptEvent = event.getScriptEvent();
		// Filter out the non-server created scripts. Do note that other plugins may call CS2s, such as the quest helper plugin.
		if (scriptEvent == null || scriptEvent.getSource() != null) {
			return;
		}
		final Object[] arguments = scriptEvent.getArguments();
		final int scriptId = (int) arguments[0];
		if (scriptId == QSRID.SPEEDRUNNING_HELPER_UPDATE)
		{
			final int ticks = (int) arguments[1];
			currTicks = ticks;
			sendMessage("setgametime " + ticks*0.6);
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (started) {
			if (event.getGameState() == GameState.LOADING ||
					event.getGameState() == GameState.LOGGED_IN ||
					event.getGameState() == GameState.CONNECTION_LOST) {
				if (paused) {
					sendMessage("resume");
					paused = false;
				}
			} else if (!paused) {
				sendMessage("pause");
				paused = true;
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
	}
	public void completeRun() {
		questsComplete = 0;
		started = false;
		sendMessage("getcurrenttimerphase");
		String msg = receiveMessage();
		loop:
		while (!msg.equals("ERROR")) {
			switch (msg) {
				case "Running":
					sendMessage("getsplitindex");
					String i = receiveMessage();
					sendMessage("skipsplit");
					sendMessage("getsplitindex");
					String j = receiveMessage();
					if (i.equals(j)) {
						split();
						break loop;
					}
					break;
				case "Paused":
					sendMessage("resume");
					break;
				case "Ended":
					sendMessage("unsplit");
					break;
				case "NotRunning":
					break loop;
			}
			sendMessage("getcurrenttimerphase");
			msg = receiveMessage();
		}

	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (started && client.getVarbitValue(QUESTS_COMPLETE_COUNTER) > questsComplete) {
			completeRun();
		}

		for (Pair<Integer, Integer> pair : varbList) {
			if (client.getVarbitValue(pair.first) == pair.second) {
				split();
				varbList.remove(pair);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: " + pair.first + "; " + pair.second, null);

			}
		}
		for (Pair<Integer, Integer> pair : varpList) {
			if (client.getVarpValue(pair.first) == pair.second) {
				split();
				varpList.remove(pair);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: " + pair.first + "; " + pair.second, null);

			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		final ItemContainer itemContainer = event.getItemContainer();
		if (itemContainer != client.getItemContainer(InventoryID.INVENTORY))
		{
			return;
		}

		for (Pair<Integer, Integer> pair : itemList) {
			if (itemContainer.count(pair.first) >= pair.second) {
				split();
				itemList.remove(pair);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: " + pair.first + "; " + pair.second, null);

			}
		}
	}

	public boolean isInSpeedrun() {
		return client.getVarbitValue(QSRID.SPEEDRUN_ACTIVE_SIGNIFIER) == QSRID.IN_RUN;
	}

	public void split() {
		sendMessage("setgametime " + (currTicks + 1) * 0.6);
		sendMessage("split");
	}
}
