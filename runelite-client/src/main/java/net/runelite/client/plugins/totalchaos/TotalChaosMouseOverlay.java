package net.runelite.client.plugins.totalchaos;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class TotalChaosMouseOverlay extends Overlay {
    private final TotalChaosPlugin plugin;
    private final Client client;
    @Inject
    private TotalChaosConfig config;
    @Inject
    public TotalChaosMouseOverlay(TotalChaosPlugin plugin, Client client) {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        drawMouse(graphics);
        return null;
    }

    private void drawMouse(Graphics2D graphics) {
        graphics.setColor(Color.red);
        if (plugin.mouseLocation != null) {
            Shape s = new Ellipse2D.Double(plugin.mouseLocation.getX(), plugin.mouseLocation.getY(), 3, 3);
            graphics.fill(s);
            graphics.draw(s);
        }

    }
}