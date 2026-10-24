package com.codex.plunder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CodexPlunderPilotConfig.GROUP)
public interface CodexPlunderPilotConfig extends Config
{
	String GROUP = "codexplunderpilot";

	@ConfigSection(
		name = "In game",
		description = "What Plunder Pilot draws over the game",
		position = 0
	)
	String inGame = "inGame";

	@ConfigSection(
		name = "Chat",
		description = "What Plunder Pilot says in the chat box",
		position = 1
	)
	String chat = "chat";

	@ConfigSection(
		name = "Panel and card",
		description = "The goal the panel counts toward and the pyramid card",
		position = 2
	)
	String panel = "panel";

	@ConfigItem(
		keyName = "goalLevel",
		name = "Goal level",
		description = "The Thieving level the Runs page counts toward",
		position = 0,
		section = panel
	)
	@Range(min = 22, max = 99)
	default int goalLevel()
	{
		return 99;
	}

	@ConfigItem(
		keyName = "highlights",
		name = "Highlight the next step",
		description = "Outline what the plan says to do next in the room: the urns worth searching, the chest or sarcophagus, the trap or the doors to try",
		position = 0,
		section = inGame
	)
	default boolean highlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "plaque",
		name = "Run plaque",
		description = "Show a small plaque during a run with the room, the clock and the next step",
		position = 1,
		section = inGame
	)
	default boolean plaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "readyPlaque",
		name = "Ready plaque",
		description = "Show the ready check's most urgent point on a plaque before a run, such as a pack too full for the artefacts",
		position = 2,
		section = inGame
	)
	default boolean readyPlaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chaseWarning",
		name = "Swarm and mummy warning",
		description = "Put a scarab swarm or mummy that goes for you first in the next step; off keeps the plan going",
		position = 3,
		section = inGame
	)
	default boolean chaseWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "packWarning",
		name = "Full pack warning",
		description = "Put dropping the cheapest artefacts first in the next step when your pack fills mid-run; off keeps the plan going",
		position = 4,
		section = inGame
	)
	default boolean packWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "summary",
		name = "Run summary in chat",
		description = "Say in the chat box what a run earned and the sceptre odds it carried when it ends",
		position = 0,
		section = chat
	)
	default boolean summary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "roomOpens",
		name = "Room openings in chat",
		description = "Say in the chat box when a Thieving level opens a deeper room, with what a run pays there",
		position = 1,
		section = chat
	)
	default boolean roomOpens()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nameOnCard",
		name = "Name on cards",
		description = "Write your character's name on the pyramid card; turn off to share the card without it",
		position = 1,
		section = panel
	)
	default boolean nameOnCard()
	{
		return true;
	}
}
