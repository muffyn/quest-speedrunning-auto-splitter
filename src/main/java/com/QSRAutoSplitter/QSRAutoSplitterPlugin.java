package com.QSRAutoSplitter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

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
	private int ticks;

	// The variables to interact with livesplit
	PrintWriter writer;
	BufferedReader reader;

	@Inject
	private Client client;

	@Inject
	private QSRAutoSplitterConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	// side panel
	private NavigationButton navButton;
	private QSRAutoSplitterPanel panel;

	// is the timer running?
	private boolean started = false;
	private boolean paused = false;
	private int initializeTimer = 2;

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

		itemList = new ArrayList<>();
		varbList = new ArrayList<>();
		varpList = new ArrayList<>();

		panel.startPanel();
	}

	@Override
	protected void shutDown()
	{
		sendMessage("pause");
		itemList = null;
		varbList = null;
		varpList = null;
		clientToolbar.removeNavigation(navButton);
		panel.disconnect();  // terminates active socket
	}

	private void setupSplits(String configStr) {
		itemList = new ArrayList<>();
		varbList = new ArrayList<>();
		varpList = new ArrayList<>();

		final String[] configList = configStr.split("\n");

		for (String line : configList) {
			final String[] args = line.split(",");
			final Pair<Integer, Integer> pair;
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
		if (initializeTimer > 0) {
			initializeTimer--;
			return;
		}
		if (!started && isInSpeedrun()) {
			started = true;
			sendMessage("reset");
			sendMessage("initgametime");
			sendMessage("starttimer");

			questsComplete = client.getVarbitValue(QSRID.QUESTS_COMPLETE_COUNTER);
			final int currQuest = client.getVarbitValue(QSRID.SPEEDRUN_QUEST_SIGNIFIER);
			final String configStr;

			switch (currQuest) {
				case QSRID.CA:   configStr = config.caList();   break;
				case QSRID.DS:   configStr = config.dsList();   break;
				case QSRID.ETC:  configStr = config.etcList();  break;
				case QSRID.PAR:  configStr = config.parList();  break;
				case QSRID.BKF:  configStr = config.bkfList();  break;
				case QSRID.VS:   configStr = config.vsList();   break;
				case QSRID.DSI:  configStr = config.dsiList();  break;
				case QSRID.DSII: configStr = config.dsiiList(); break;
				case QSRID.TOH:  configStr = config.tohList();  break;
				case QSRID.XMS:  configStr = config.xmsList();  break;
				case QSRID.SOTF: configStr = config.sotfList(); break;
				case QSRID.BIM:  configStr = config.bimList();  break;
				case QSRID.AKD:  configStr = config.akdList();  break;
				case QSRID.TOE:  configStr = config.toeList();  break;
				case QSRID.BCS:  configStr = config.bcsList();  break;
				default:
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "QSR: quest not found; plugin needs to be updated", null);
					configStr = "";
					break;
			}
			setupSplits(configStr);

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
			ticks = (int) arguments[1];
			sendMessage("setgametime " + ticks*0.6);
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGING_IN || event.getGameState() == GameState.HOPPING) {
			initializeTimer = 2;
		}
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

	public void completeRun() {
		started = false;
		sendMessage("getcurrenttimerphase");
		String msg = receiveMessage();

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
						return;
					}
					break;
				case "Paused":
					sendMessage("resume");
					break;
				case "Ended":
					sendMessage("unsplit");
					break;
				case "NotRunning":
					return;
			}
			sendMessage("getcurrenttimerphase");
			msg = receiveMessage();
		}

	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		final ItemContainer itemContainer = event.getItemContainer();
		if (itemContainer != client.getItemContainer(InventoryID.INVENTORY)) {
			return;
		}

		for (Pair<Integer, Integer> pair : itemList) {
			if (itemContainer.count(pair.first) >= pair.second) {
				split();
				itemList.remove(pair);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (started && client.getVarbitValue(QSRID.QUESTS_COMPLETE_COUNTER) > questsComplete) {
			completeRun();
		}

		for (Pair<Integer, Integer> pair : varbList) {
			if (client.getVarbitValue(pair.first) == pair.second) {
				split();
				varbList.remove(pair);
			}
		}

		for (Pair<Integer, Integer> pair : varpList) {
			if (client.getVarpValue(pair.first) == pair.second) {
				split();
				varpList.remove(pair);
			}
		}
	}

	public boolean isInSpeedrun() {
		return client.getVarbitValue(QSRID.SPEEDRUN_ACTIVE_SIGNIFIER) == QSRID.IN_RUN;
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

	public void split() {
		sendMessage("pausegametime");
		sendMessage("setgametime " + BigDecimal.valueOf((ticks + 1) * 0.6).setScale(1, RoundingMode.HALF_UP));
		sendMessage("split");
		sendMessage("unpausegametime");
	}
}
