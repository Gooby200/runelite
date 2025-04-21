package net.runelite.client.plugins.splasher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.Utils.Core;
import net.runelite.client.plugins.Utils.Walking;

import javax.inject.Inject;
import java.util.concurrent.Executors;

@PluginDescriptor(
        name = "Splasher",
        tags = {"Gaston"},
        enabledByDefault = false
)
@Slf4j
public class SplasherPlugin extends Plugin {
    @Inject Client client;
    @Inject ClientThread clientThread;
    @Inject Core core;
    private Long timeUntilNextClick = null;
    private long afkTimer = System.currentTimeMillis();

    @Subscribe
    public void onGameTick(GameTick tick) {
        Player player = client.getLocalPlayer();
        if (System.currentTimeMillis() >= afkTimer || !player.isInteracting() || (player.getAnimation() == -1 && player.getInteracting() == null)) {
            System.out.println(1);
            if (timeUntilNextClick == null)
                timeUntilNextClick = System.currentTimeMillis() + core.getRandomIntBetweenRange(0, 2 * 60000);
        }

        if (timeUntilNextClick != null && System.currentTimeMillis() >= timeUntilNextClick) {
            findAndClickSeagull();

            //check to see if we're attacking or not because if we missclick, we don't want to wait a whole 2 minutes
            if (System.currentTimeMillis() >= afkTimer || !player.isInteracting() || (player.getAnimation() == -1 && player.getInteracting() == null)) {
                timeUntilNextClick = System.currentTimeMillis() + core.getRandomIntBetweenRange(0, 10000);
            } else {
                timeUntilNextClick = null;
            }
        }
    }

    private void findAndClickSeagull() {
        Player player = client.getLocalPlayer();
        NPC seagull = core.findNearestNpc("Seagull");
        System.out.println(3);
        if (player.isInteracting() && player.getInteracting().getName() == "Seagull") {
            seagull = (NPC)player.getInteracting();
            System.out.println(4);
        }

        if (seagull != null) {
            System.out.println(5);
            //if the seagull isn't interacting or the seagull is already interacting with us, that's the one we want
            //otherwise its attacking someone else
            if (!seagull.isInteracting() || seagull.getInteracting() == client.getLocalPlayer()) { //maybe if this isn't true, find the next closest seagull?
                System.out.println(6);
                NPC finalSeagull = seagull;
                Executors.newSingleThreadExecutor().submit(() -> {
                    core.attackNPCDirect(finalSeagull);
                });
                //core.attackNPCDirect(finalSeagull);
                afkTimer = System.currentTimeMillis() + (20 * 60000);
            }
        }
    }

}
