package net.runelite.client.plugins.goblintrainer;

import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Optional;

public class GoblinTrainerOverlay extends Overlay {
    private final GoblinTrainerPlugin plugin;
    private final Client client;
    int CULL_LINE_OF_SIGHT_RANGE = 10;
    private static final Color LINE_OF_SIGHT_COLOR = new Color(204, 42, 219);
    @Inject
    private GoblinTrainerPluginConfig config;
    float borderWidth = 2.0f;
    @Inject
    public GoblinTrainerOverlay(GoblinTrainerPlugin plugin, Client client) {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
/*        if (plugin.isLowHealth()) {
            highlightFood(graphics);
        }*/
        //setLayer(OverlayLayer.ALWAYS_ON_TOP);
        //highlightFood(graphics);

//        if (!plugin.isInCombat()) { // Only highlight goblins if not in combat
//            setLayer(OverlayLayer.ABOVE_SCENE);
//            highlightNearestGoblin(graphics);
//        }
        //setLayer(OverlayLayer.ABOVE_SCENE);
        drawFightDistance(graphics);
        //renderLineOfSight(graphics);
        return null;
    }

    private void drawFightDistance(Graphics2D graphics) {
        if (this.plugin.fightLocation != null) {
            for (int x = this.plugin.fightLocation.getX() - config.fightDistanceX(); x <= this.plugin.fightLocation.getX() + config.fightDistanceX(); x++) {
                for (int y = this.plugin.fightLocation.getY() - config.fightDistanceY(); y <= this.plugin.fightLocation.getY() + config.fightDistanceY(); y++) {
                    WorldPoint targetLocation = new WorldPoint(x, y, plugin.fightLocation.getPlane());
                    LocalPoint lp = LocalPoint.fromWorld(client, targetLocation);
                    if (lp == null)
                    {
                        return;
                    }

                    Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                    if (poly == null)
                    {
                        return;
                    }

                    OverlayUtil.renderPolygon(graphics, poly, LINE_OF_SIGHT_COLOR);
                }
            }
        }
    }

    private void renderLineOfSight(Graphics2D graphics)
    {
        WorldArea area = client.getLocalPlayer().getWorldArea();
        for (int x = area.getX() - CULL_LINE_OF_SIGHT_RANGE; x <= area.getX() + CULL_LINE_OF_SIGHT_RANGE; x++)
        {
            for (int y = area.getY() - CULL_LINE_OF_SIGHT_RANGE; y <= area.getY() + CULL_LINE_OF_SIGHT_RANGE; y++)
            {
                if (x == area.getX() && y == area.getY())
                {
                    continue;
                }
                renderTileIfHasLineOfSight(graphics, area, x, y);
            }
        }
    }

    private void renderTileIfHasLineOfSight(Graphics2D graphics, WorldArea start, int targetX, int targetY)
    {
        WorldPoint targetLocation = new WorldPoint(targetX, targetY, start.getPlane());

        // Running the line of sight algorithm 100 times per frame doesn't
        // seem to use much CPU time, however rendering 100 tiles does
        if (start.hasLineOfSightTo(client.getTopLevelWorldView(), targetLocation))
        {
            LocalPoint lp = LocalPoint.fromWorld(client, targetLocation);
            if (lp == null)
            {
                return;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null)
            {
                return;
            }

            OverlayUtil.renderPolygon(graphics, poly, LINE_OF_SIGHT_COLOR);
        }
    }

    private void highlightFood(Graphics2D graphics) {
        if (plugin.hasHerring()) {
            setLayer(OverlayLayer.ALWAYS_ON_TOP);
            highlightInventoryItem(graphics, ItemID.HERRING, Color.GREEN);
        } else {
            setLayer(OverlayLayer.ABOVE_SCENE);
            highlightBankPath(graphics);
        }
    }

    private void highlightInventoryItem(Graphics2D graphics, int itemId, Color color) {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
            return;
        }

        for (Widget item : inventoryWidget.getDynamicChildren()) {
            if (item.getItemId() == ItemID.HERRING) {

                setLayer(OverlayLayer.ALWAYS_ON_TOP);
                AffineTransform transform = AffineTransform.getTranslateInstance(-5, -20);
                Shape correctedShape = transform.createTransformedShape(item.getBounds());

                graphics.setColor(Color.RED);
                graphics.fill(correctedShape);

/*                AffineTransform transform = AffineTransform.getTranslateInstance(-5, -20);
                Shape correctedShape = transform.createTransformedShape(goblinShape);

                graphics.setColor(new Color(0, 255, 255, 255)); // Semi-transparent red
                graphics.fill(correctedShape); // Fill the goblin
                graphics.draw(correctedShape); // Keep an outline for visibility*/

                //OverlayUtil.renderPolygon(graphics, item.getBounds(), color);
                return;
            }
        }
    }

    private void highlightNearestGoblin(Graphics2D graphics) {
        List<NPC> npcs = client.getNpcs();
        Optional<NPC> nearestGoblin = npcs.stream()
                .filter(npc -> npc.getName().equals("Goblin")) // Only consider goblins
                .filter(npc -> npc.getInteracting() == null) // Ignore goblins that are already in combat
                .min((a, b) -> {
                    int distA = client.getLocalPlayer().getWorldLocation().distanceTo(a.getWorldLocation());
                    int distB = client.getLocalPlayer().getWorldLocation().distanceTo(b.getWorldLocation());
                    return Integer.compare(distA, distB);
                });
        nearestGoblin.ifPresent(npc -> {
            Shape goblinShape = npc.getConvexHull();
            if (goblinShape != null) {
                // Adjust the y-position slightly if needed
                AffineTransform transform = AffineTransform.getTranslateInstance(-5, -20);
                Shape correctedShape = transform.createTransformedShape(goblinShape);

                graphics.setColor(new Color(0, 255, 255, 255)); // Semi-transparent red
                graphics.fill(correctedShape); // Fill the goblin
                graphics.draw(correctedShape); // Keep an outline for visibility
            }
        });
    }

    private void highlightBankPath(Graphics2D graphics) {
        WorldPoint bankLocation = new WorldPoint(3253, 3420, 0); // Example bank location
        LocalPoint localBankLocation = LocalPoint.fromWorld(client, bankLocation);

        if (localBankLocation != null) {
            Polygon poly = Perspective.getCanvasTilePoly(client, localBankLocation);
            if (poly != null) {
                graphics.setColor(new Color(255, 255, 0, 100)); // Semi-transparent yellow
                graphics.fill(poly);
                graphics.setColor(Color.YELLOW);
                graphics.draw(poly);
            }
        }
    }


}
