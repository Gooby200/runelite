package net.runelite.client.plugins.bigboneburial;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.aiofighter.AIOFighterMouseOverlay;
import net.runelite.client.plugins.aiofighter.AIOFighterOverlay;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Big Bone Burial",
        enabledByDefault = false,
        tags = {"gaston"}
)
public class BigBoneBurialPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private boolean busy = false;

    private static final int BIG_BONE_ID = ItemID.BIG_BONES;
    Point mouseLocation = null;
    private static final Random random = new Random();
    private long nextActionTime = System.currentTimeMillis();
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BigBoneBurialMouseOverlay mouseOverlay;

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

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (busy || client.getLocalPlayer() == null || client.getGameState() != GameState.LOGGED_IN)
            return;

        if (System.currentTimeMillis() < nextActionTime)
            return;

        clientThread.invoke(() ->
        {
            busy = true;
            System.out.println(1);
            // If bank is open, withdraw bones
            if (isBankOpen()) {
                if (hasBigBones()) {
                    //close bank
                    closeBank();
                } else {
                    System.out.println(2);
                    withdrawBigBones();
                }
                busy = false;
                nextActionTime = System.currentTimeMillis() + getRandomBetween(1000, 1500);
                return;
            }

            // Check if inventory contains Big Bones
            if (hasBigBones())
            {
                System.out.println(3);
                // Make sure inventory tab is open
                if (!isInventoryOpen())
                {
                    System.out.println(4);
                    openInventoryTab();
                    busy = false;
                    return;
                }

                // Find and bury one Big Bone
                if (buryBone())
                {
                    System.out.println(5);
                    busy = false;
                    return;
                }
            }
            else
            {
                System.out.println(6);
                // Open bank if we have no bones
                if (isBankOpen() == false) {
                    System.out.println(7);
                    if (!openBank())
                    {
                        System.out.println(8);
                        log.debug("No nearby GE booth found.");
                    }
                }
            }

            busy = false;
        });
    }

    private boolean isInventoryOpen() {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
            return false;
        }

        return true;
    }

    private boolean isBankOpen() {
        Widget bank = client.getWidget(WidgetInfo.BANK_CONTAINER);
        if (bank == null || bank.isHidden()) {
            return false; // Bank isn't open or widget is hidden
        }
        return true;
    }

    private boolean closeBank()
    {
        Widget closeButton = client.getWidget(786434).getChild(11);
        if (closeButton == null || closeButton.isHidden())
        {
            return false; // Bank isn't open or widget is hidden
        }

        Point point = getRandomPointInBounds(closeButton.getBounds());
        if (point != null) {
            sendMoveAndClick(point.getX(), point.getY());
            return true;
        }

        return false;
    }

    private boolean hasBigBones()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return false;

        for (Item item : inventory.getItems())
        {
            if (item.getId() == BIG_BONE_ID)
                return true;
        }
        return false;
    }

    private boolean buryBone()
    {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null || inventoryWidget.getChildren() == null)
            return false;

        System.out.println(21);
        for (Widget item : inventoryWidget.getChildren())
        {
            if (item.getItemId() == BIG_BONE_ID)
            {
                System.out.println(22);
                Point point = getRandomPointInBounds(item.getBounds());
                if (point != null) {
                    sendMoveAndClick(point.getX(), point.getY());
                    nextActionTime = System.currentTimeMillis() + getRandomBetween(1000, 1800);
                    return true;
                }
            }
        }
        return false;
    }

    public int getRandomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void openInventoryTab()
    {
        //send inventory tab coords
        Widget inventoryTab = client.getWidget(WidgetInfo.INVENTORY.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB);
        // If it's null, try the fixed mode widget
        if (inventoryTab == null) {
            inventoryTab = client.getWidget(WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB);
        }
        if (inventoryTab != null) {
            // Get the bounds of the inventory tab button
            Point point = getRandomPointInBounds(inventoryTab.getBounds());
            if (point != null) {
                //TCPClient.sendClick(client, point);
                sendMoveAndClick(point.getX(), point.getY());
            }
        }
    }

    public void sendClickEvent(int x, int y) {
        Canvas canvas = client.getCanvas();
        java.awt.Point point = new java.awt.Point(x, y);
        SwingUtilities.convertPointToScreen(point, canvas);

        // Press mouse button
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        // Release mouse button
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        // Click event (some cases require this)
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));
    }

    public void sendMoveEvent(int x, int y) {
        Canvas canvas = client.getCanvas();
        java.awt.Point point = new java.awt.Point(x, y);
        SwingUtilities.convertPointToScreen(point, canvas);
        mouseLocation = new Point(x, y);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 0, false, 0));
    }

    public void sendMoveAndClick(int x, int y) {
        // First, move the mouse
        sendMoveEvent(x, y);

        // Use a Swing Timer to wait 100ms and then trigger the click event without blocking the game thread
        Timer timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // After 100ms, send the click event
                sendClickEvent(x, y);

                // Stop the timer after the action is performed
                ((Timer) e.getSource()).stop();
            }
        });

        timer.setRepeats(false); // Make sure it only fires once
        timer.start(); // Start the timer
    }

    private static Point getRandomPointInBounds(Rectangle bounds) {
        if (bounds == null) {
            return null;
        }

        int x = bounds.x + random.nextInt(bounds.width);
        int y = bounds.y + random.nextInt(bounds.height);

        return new Point(x, y);
    }


    private boolean openBank()
    {
        WallObject closestObject = null;
        int closestDistance = Integer.MAX_VALUE;
        Player localPlayer = client.getLocalPlayer();
        WorldPoint playerLocation = localPlayer.getWorldLocation();
        Tile[][][] tiles = client.getScene().getTiles();

        for (int x = 0; x < tiles[0].length; x++)
        {
            for (int y = 0; y < tiles[0][x].length; y++)
            {
                Tile tile = tiles[0][x][y];
                if (tile == null)
                    continue;

                WallObject wallObject = tile.getWallObject();
                if (wallObject != null && wallObject.getId() == 10060)
                {
                    WorldPoint wallLoc = WorldPoint.fromLocal(client, wallObject.getLocalLocation());
                    int distance = wallLoc.distanceTo(playerLocation);

                    if (distance < closestDistance)
                    {
                        closestObject = wallObject;
                        closestDistance = distance;
                    }
                }
            }
        }

        if (closestObject != null) {
            Point point = getRandomPointInBounds(closestObject.getClickbox().getBounds());
            if (point != null) {
                sendMoveAndClick(point.getX(), point.getY());
                nextActionTime = System.currentTimeMillis() + getRandomBetween(600, 800);
                return true;
            }
        }

        return false;
    }


    private void withdrawBigBones()
    {
        Widget bankWidget = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (bankWidget == null || bankWidget.getChildren() == null)
            return;

        for (Widget item : bankWidget.getChildren())
        {
            if (item.getItemId() == BIG_BONE_ID && !item.isHidden()) {
                Point point = getRandomPointInBounds(item.getBounds());
                if (point != null) {
                    sendMoveAndClick(point.getX(), point.getY());
                    break;
                }
            }
        }
    }
}
