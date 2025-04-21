package net.runelite.client.plugins.aiofighter;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class AIOFighterMouseOverlay extends Overlay {
    private final AIOFighterPlugin plugin;
    private final Client client;
    @Inject
    public AIOFighterMouseOverlay(AIOFighterPlugin plugin, Client client) {
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
