package net.runelite.client.plugins.totalchaos;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.Utils.Core;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@PluginDescriptor(
        name = "Total Chaos",
        enabledByDefault = false,
        tags = {"gaston"}
)
public class TotalChaosPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Core core;
    Point mouseLocation = null;
    private long nextActionTime = System.currentTimeMillis();
    private boolean running = true;
    private String action = "Banking";

    @Override
    protected void startUp()
    {
        //overlayManager.add(mouseOverlay);
        log.info("Big Bone Burial plugin started!");
    }

    @Override
    protected void shutDown()
    {
        //verlayManager.remove(mouseOverlay);
        log.info("Big Bone Burial plugin stopped!");
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        NPC aubury = core.findNearestNpc("Aubury");
        if (aubury != null && aubury.getComposition().isVisible()) {
            Executors.newSingleThreadExecutor().submit(() -> {
                //core.trade
            });
        }
    }

    private void loop() {
        while (running) {
            if (System.currentTimeMillis() < nextActionTime)
                continue;

            //get the game state


        }
    }

    public String getGameState() {
        //if we have other things in our inventory that isn't chaos runes, coins, or chaos pouches, go to bank
        //if we have coins in our inventory, and we have over 15000 coins, walk to shop
        //if we have chaos runes in our inventory and we have coins in our inventory and we have less than 15000 coins, go to GE to sell

        return "";
    }

    public void buyFromShop() {
        //trade npc
        //if 35 packs, buy 50
    }

    public int getRandomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
