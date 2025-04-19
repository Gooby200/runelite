package net.runelite.client.plugins.splasher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.Utils.Core;
import net.runelite.client.plugins.Utils.Walking;

import javax.inject.Inject;

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
    @Inject Walking walking;

    @Subscribe
    public void onGameTick(GameTick tick) {
        //if we are not interacting or our 20 minute timer is up, we should click
        if (!core.isInteracting()) {
            findAndClickSeagull(null);
        } else if (core.isInteracting()) { //if we are interacting and our 20 minute timer is up, click on the npc that is interacting with us
            NPC seagull = (NPC)client.getLocalPlayer().getInteracting();
            findAndClickSeagull(seagull);
        }
    }

    private void findAndClickSeagull(NPC seagull) {
        if (seagull == null) {
            seagull = core.findNearestNpc("Seagull");
        }

        if (seagull != null) {
            //if the seagull isn't interacting or the seagull is already interacting with us, that's the one we want
            //otherwise its attacking someone else
            if (!seagull.isInteracting() || seagull.getInteracting() == client.getLocalPlayer()) {
                core.attackNPCDirect(seagull);
            }
        }
    }

}
