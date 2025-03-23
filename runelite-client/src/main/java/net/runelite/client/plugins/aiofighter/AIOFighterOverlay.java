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

public class AIOFighterOverlay extends Overlay {
    private final AIOFighterPlugin plugin;
    private final Client client;
    int CULL_LINE_OF_SIGHT_RANGE = 10;
    private static final Color LINE_OF_SIGHT_COLOR = new Color(204, 42, 219);
    @Inject
    private AIOFighterConfig config;
    float borderWidth = 2.0f;
    @Inject
    public AIOFighterOverlay(AIOFighterPlugin plugin, Client client) {
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

}
