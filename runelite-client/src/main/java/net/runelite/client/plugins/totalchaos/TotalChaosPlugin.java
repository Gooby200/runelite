package net.runelite.client.plugins.totalchaos;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@PluginDescriptor(
        name = "Total Chaos"
)
public class TotalChaosPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TotalChaosMouseOverlay mouseOverlay;
    Point mouseLocation = null;
    private long nextActionTime = System.currentTimeMillis();
    private boolean running = true;
    private String action = "Banking";

    @Override
    protected void startUp()
    {
        overlayManager.add(mouseOverlay);
        log.info("Big Bone Burial plugin started!");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(mouseOverlay);
        log.info("Big Bone Burial plugin stopped!");
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

    public int getRandomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
