package net.runelite.client.plugins.goblintrainer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface GoblinTrainerPluginConfig extends Config
{
	@ConfigItem(
			keyName = "npcName",
			name = "NPC Name",
			description = "The name of the NPC you want to fight",
			position = 1
	)
	default String npcName()
	{
		return "Goblin";
	}

	@ConfigItem(
			keyName = "foodName",
			name = "Food Name",
			description = "The name of the food you want to eat",
			position = 2
	)
	default String foodName()
	{
		return "Herring";
	}

	@ConfigItem(
			keyName = "fightDistanceX",
			name = "Fight Distance X",
			description = "Tiles around you that you want to fight",
			position = 3
	)
	default int fightDistanceX()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "fightDistanceY",
			name = "Fight Distance Y",
			description = "Tiles around you that you want to fight",
			position = 4
	)
	default int fightDistanceY()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "eatPercentage",
			name = "Eat at Percent",
			description = "0-100 percentage to eat at",
			position = 5
	)
	default int eatPercentage()
	{
		return 50;
	}

	@ConfigItem(
			keyName = "pause",
			name = "Pause",
			description = "Pauses the script",
			position = 6
	)
	default boolean isPaused() { return false; }

	@ConfigItem(
			keyName = "pickUpBigBones",
			name = "Pick Up Big Bones?",
			description = "Picks up big bones and buries them",
			position = 7
	)
	default boolean shouldPickUpBigBones() { return false; }
}
