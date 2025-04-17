package net.runelite.client.plugins.totalchaos;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("total chaos")
public interface TotalChaosConfig extends Config {
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
}
