package net.runelite.client.plugins.accountrester;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

@PluginDescriptor(
        name = "Account Rester",
        description = "Rests your account by logging back in",
        tags = {"external", "google", "integration"}
)
@Slf4j
public class AccountRester extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    private Long canLogin = null;
    private static final Random random = new Random();
    private long waitUntil = System.currentTimeMillis();

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() != GameState.LOGIN_SCREEN) {
            canLogin = null;
        }

        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            clientThread.invokeLater(() -> {
                while (canLogin == null || System.currentTimeMillis() <= canLogin) {
                    System.out.println(1);
                    //perform action to log back in
                    if (canLogin == null) {
                        System.out.println(2);
                        canLogin = System.currentTimeMillis() + 10000; //+ getRandomBetween(15000, 180000);
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (System.currentTimeMillis() > canLogin) {
                    System.out.println(3);
                    //perform login here
                    int baseX = 380; // Approximate X position of "Play Now"
                    int baseY = 265; // Approximate Y position
                    int offset = 10;

                    int x = baseX + (int)(Math.random() * offset);
                    int y = baseY + (int)(Math.random() * offset);
                    sendMoveAndClick(x, y);
                }
            });
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (client.getGameState() == GameState.LOGGED_IN && System.currentTimeMillis() > waitUntil) {
            //check to see if we have the click here to play button
            Widget clickHereToPlay = client.getWidget(24772680);
            if (clickHereToPlay != null && clickHereToPlay.isHidden() == false) {
                CountDownLatch latch = new CountDownLatch(1);
                latch.countDown();
                try {
                    latch.await();
                    Thread.sleep(getRandomBetween(2000, 5000));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(5);
                Point point = getRandomPointInBounds(clickHereToPlay.getBounds());
                sendMoveAndClick(point.getX(), point.getY());

                waitUntil = System.currentTimeMillis() + 2000;
            }
        }
    }

    public long getRandomBetween(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static net.runelite.api.Point getRandomPointInBounds(Rectangle bounds) {
        if (bounds == null) {
            return null;
        }

        int x = bounds.x + random.nextInt(bounds.width);
        int y = bounds.y + random.nextInt(bounds.height);

        return new net.runelite.api.Point(x, y);
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

    public void sendMoveEvent(int x, int y) {
        Canvas canvas = client.getCanvas();
        java.awt.Point point = new java.awt.Point(x, y);
        SwingUtilities.convertPointToScreen(point, canvas);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 0, false, 0));
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
}
