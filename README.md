# Quest Speedrunning Auto Splitter

Be sure to **download the project** and open all of the files in LiveSplit Templates. Otherwise, you won't have the formatting or the splits. 

Using this plugin requires the LiveSplit program with the LiveSplit server component.

Installation and setup guide can be found here:

[LiveSplit](https://livesplit.org/downloads/)

[LiveSplit Server](https://github.com/LiveSplit/LiveSplit.Server)

## How to use
Download LiveSplit and the LiveSplit server component.

Turn the plugin on and make sure the port in the plugin settings match your LiveSplit server port.

![config](readme_images/config.png)

Start LiveSplit and start the LiveSplit server (right click LS -> control -> start server).
Make sure to add the LS server to your layout, otherwise you won't see "start server" under control.

![lsserver](readme_images/livesplit.png)

Open the sidebar and click "Connect".
If the status turns green it means you have a connection to your LiveSplit server.
If it stays red something went wrong, most likely you did not start the LiveSplit server
or you have mismatching ports in the plugin settings and the LiveSplit server settings.

![sidebar](readme_images/panel.png)

If your status is green you are good to go.

## Splits
Make sure you have `Compare Against > Game Time` selected on LiveSplit, or the timer will be slightly out of sync.

Open the plugin config to modify what events the plugin will split on.
The first element is just for naming, and is completely ignored.

### Format

`<name>,0,<itemID>(,<quantity>)`

Splits when an item matching the ID enters your inventory for the first time.
If a quantity is given, it will split when you first have that many of the item in your inventory.

> You can find item IDs on the [OSRS Wiki Item IDs page](https://oldschool.runescape.wiki/w/Item_IDs).

`<name>,1,<varb>,<value>`

Splits when the varbit (`varb`) changes to that value for the first time.

`<name>,2,<varp>,<value>`

Splits when the varplayer (`varp`) changes to that value for the first time.

> See the OSRS Wiki references for [varbits](https://oldschool.runescape.wiki/w/RuneScape:Varbit) and [varplayers](https://oldschool.runescape.wiki/w/RuneScape:Varplayer), or use RuneLite’s Var Inspector after enabling [developer mode](https://github.com/runelite/runelite/wiki/Using-the-client-developer-tools).

---

Want to use this plugin but need help? Ask Muffyn#0971 or https://discord.gg/m3AaNEWJax. I love speedrunning and want this tool to be as convenient as possible for anybody who wants to try :)
