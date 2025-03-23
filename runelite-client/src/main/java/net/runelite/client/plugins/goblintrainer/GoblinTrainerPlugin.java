package net.runelite.client.plugins.goblintrainer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "Goblin Trainer",
		description = "Highlights actions to efficiently train combat on goblins",
		tags = {"combat", "training", "highlight", "food"}
)
public class GoblinTrainerPlugin extends Plugin
{
	private static final Random random = new Random();
	private static final int GOBLIN_NPC_ID = 302; // Adjust for different goblin types if necessary
	private static final int HERRING_ID = 347;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GoblinTrainerOverlay overlay;

	@Inject
	private GoblinTrainerPluginConfig config;

	@Inject
	private ItemManager itemManager;

	private boolean lowHealth = false;
	private boolean hasHerring = false;
	private boolean inCombat = false;
	public WorldPoint fightLocation = null;
	private String hoverNPC = null;
	private String hoverAction = null;
	private Set<WorldPoint> acceptableWorldPoints = new HashSet<>();

	@Override
	protected void startUp() throws Exception
	{
		System.out.println("Example started!!");
		fightLocation = client.getLocalPlayer().getWorldLocation();

		setCameraPitch();

		//TCPClient.connect();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		System.out.println("Example stopped!");
		//TCPClient.disconnect();
		overlayManager.remove(overlay);
	}

	public void setCameraPitch() {
		int pitch = client.getCameraPitch();
		if (pitch >= 0 && pitch <= 383) {  // Validate pitch range (check if 383 is valid within the game)
			client.setCameraPitchTarget(383);
		} else {
			System.out.println("Invalid pitch value. Must be between 0 and 383.");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) throws InterruptedException {
		if (config.isPaused())
			return;

//		Widget hoverWidget = client.getWidget(WidgetInfo.DIALOG_OPTION);
//		if (hoverWidget != null && hoverWidget.getText() != null) {
//			// If the widget text contains an NPC name, it's probably an NPC being hovered
//			System.out.println("Currently hovering over: " + hoverWidget.getText());
//		}

//		Widget logoutButton = client.getWidget(11927564);
//		System.out.println(logoutButton);
//		System.out.println(logoutButton.isHidden());
//		System.out.println(logoutButton.isSelfHidden());

		checkHealth();
		checkInventory();
		checkCombatStatus();

		//make sure we hit click to continue if it exists
		Widget clickToContinue = client.getWidget(15269891);
		if (clickToContinue != null && clickToContinue.isHidden() == false) {
			//System.out.println("test");
			Point point = getRandomPointInBounds(clickToContinue.getBounds());
			if (point != null) {
				sendMoveAndClick(point.getX(), point.getY());
			}
		}

		if (lowHealth) {
			if (!inCombat) {
				Point logoutButtonLocation = getLogoutButtonLocation();
				if (logoutButtonLocation != null) {
					sendMoveAndClick(logoutButtonLocation.getX(), logoutButtonLocation.getY());
					return;
				}
			}

			//open inventory
			if (isInventoryOpen()) {
				Point point;
				if (hasHerring) {
					point = getHerringLocation();
				} else {
					point = getXLocation();
				}
				if (point != null) {
					//TCPClient.sendClick(client, point);
					sendMoveAndClick(point.getX(), point.getY());
				}
			} else {
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
		} else {
			if (inCombat || client.getLocalPlayer().isInteracting()) {
				//do nothing and keep attacking
			} else {
				if (config.shouldPickUpBigBones()) {
					//check for big bones on the ground
					//if any exist, pick it up
					//wait a second and check if we have it in our inventory and it no longer exists on the ground, if so, bury it
				}
				//find a goblin and send the coords
				Point point = getNearestGoblin();
				if (point != null) {
					//System.out.println(hoverAction);
					//System.out.println(hoverNPC);
					sendMoveEvent(point.getX(), point.getY());

					ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
					CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS, scheduler).execute(() -> {
						MenuEntry[] menuEntries = client.getMenuEntries();

						if (menuEntries != null && menuEntries.length > 0) {
							// Sort by MenuAction ID (smallest to largest)
							Arrays.sort(menuEntries, Comparator.comparingInt(entry -> entry.getType().getId()));

							// Get the first menu entry after sorting
							MenuEntry firstEntry = menuEntries[0];

							if (firstEntry.getTarget() != null && firstEntry.getOption() != null) {
								// Get NPC name and option
								hoverAction = firstEntry.getOption();
								hoverNPC = firstEntry.getTarget();
							} else {
								hoverAction = null;
								hoverNPC = null;
							}
						} else {
							hoverAction = null;
							hoverNPC = null;
						}

						if (hoverAction != null && hoverNPC != null) {
							if (hoverAction.toLowerCase().contains("attack") && hoverNPC.toLowerCase().contains(config.npcName().toLowerCase())) {
								sendClickEvent(point.getX(), point.getY());
								//System.out.println(2);
							} else {
								//right click
								//System.out.println(3);
							}
						} else {
							//System.out.println(4);
						}
					});

//					if (canLeftClick()) {
//						sendClickEvent(point.getX(), point.getY());
//					} else {
//
//					}
					//TCPClient.sendClick(client, point);
				}
			}
		}
	}

	private boolean canLeftClick() {
		MenuEntry[] menuEntries = client.getMenuEntries();

		if (menuEntries == null || menuEntries.length == 0)
			return false;

		MenuEntry entry = menuEntries[0];
		//for (MenuEntry entry : menuEntries) {
			String option = entry.getOption(); // The option text (e.g., "Attack", "Talk-to", etc.)
			String target = entry.getTarget(); // The target (e.g., the NPC name or object name)

			System.out.println("Option: " + option);
			System.out.println("Target: " + target);

			// You can use this information to check for specific actions
			if (target != null && target.toLowerCase().contains(config.npcName().toLowerCase())) {
				// For example, checking if the option is related to a goblin
				if (option.equalsIgnoreCase("Attack")) {
					// Do something when attacking a goblin
					return true;
				}
			}
		//}
		return false;
	}

	private Point getXLocation() {
		Widget logoutButton = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_LOGOUT_BUTTON);
		if (logoutButton == null || logoutButton.isHidden()) {
			logoutButton = client.getWidget(WidgetInfo.FIXED_VIEWPORT_LOGOUT_TAB);
			if (logoutButton == null || logoutButton.isHidden()) {
				return null;
			}
		}

		return getRandomPointInBounds(logoutButton.getBounds());
	}

	private Point getLogoutButtonLocation() {
		Widget logoutButton = client.getWidget(11927564);
//		System.out.println(logoutButton);
//		System.out.println(logoutButton.isHidden());
//		System.out.println(logoutButton.isSelfHidden());
		if (logoutButton == null || logoutButton.isHidden()) {
			return null;
		}

		return getRandomPointInBounds(logoutButton.getBounds());
	}

	private void doLogout() {
		if (getLogoutButtonLocation() == null) {
			Point point = getXLocation();
			if (point != null) {
				//System.out.println("1," + point);
				sendMoveAndClick(point.getX(), point.getY());
				Timer timer = new Timer(10, new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						Point point = getLogoutButtonLocation();
						if (point != null) {
							//System.out.println("2," + point);
							// After 100ms, send the click event
							sendClickEvent(point.getX(), point.getY());
						}
						// Stop the timer after the action is performed
						((Timer) e.getSource()).stop();
					}
				});
			}
		} else {
			Point point = getLogoutButtonLocation();
			if (point != null) {
				sendMoveAndClick(point.getX(), point.getY());
			}
		}
	}

	private Point getHerringLocation() {
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
			return null;
		}

		for (Widget item : inventoryWidget.getDynamicChildren()) {
			if (item.getName().toLowerCase().contains(config.foodName().toLowerCase())) {
				return getRandomPointInBounds(item.getBounds());
			}
		}

		return null;
	}

	private static Point getRandomPointInBounds(Rectangle bounds) {
		if (bounds == null) {
			return null;
		}

		int x = bounds.x + random.nextInt(bounds.width);
		int y = bounds.y + random.nextInt(bounds.height);

		return new Point(x, y);
	}

	private boolean isReachable(NPC npc) {
		if (npc == null || client.getLocalPlayer() == null) {
			return false;
		}

		WorldPoint npcLocation = npc.getWorldLocation();
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

		// If the NPC is too far away, assume it's unreachable
		if (playerLocation.distanceTo(npcLocation) > 15) {
			return false;
		}

		// Use collision data to check for a walkable path
		return hasPathTo(playerLocation, npcLocation);
	}

	private boolean hasPathTo(WorldPoint start, WorldPoint end) {
		if (client.getCollisionMaps() == null) {
			return false;
		}

		int[][] collisionData = client.getCollisionMaps()[client.getPlane()].getFlags();
		Queue<WorldPoint> queue = new LinkedList<>();
		Set<WorldPoint> visited = new HashSet<>();
		queue.add(start);

		while (!queue.isEmpty()) {
			WorldPoint current = queue.poll();

			if (current.equals(end)) {
				return true; // Path found!
			}

			visited.add(current);

			for (WorldPoint neighbor : getNeighbors(current)) {
				if (!visited.contains(neighbor) && canMoveTo(neighbor, collisionData)) {
					queue.add(neighbor);
				}
			}
		}
		return false; // No path found
	}

	private boolean canMoveTo(WorldPoint point, int[][] collisionData) {
		int x = point.getX() - client.getBaseX();
		int y = point.getY() - client.getBaseY();

		if (x < 0 || y < 0 || x >= collisionData.length || y >= collisionData[0].length) {
			return false;
		}

		int flag = collisionData[x][y];

		// 0x100 means an object is blocking movement (e.g., walls, doors)
		return (flag & 0x100) == 0;
	}

	private List<WorldPoint> getNeighbors(WorldPoint point) {
		return Arrays.asList(
				point.dx(1),  // East
				point.dx(-1), // West
				point.dy(1),  // North
				point.dy(-1)  // South
		);
	}


	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		//System.out.println(event.getType());
		if (event.getType() == 10) { // Check if it's an NPC option
			String option = event.getOption(); // Gets the first option
			String target = event.getTarget(); // Gets the NPC name

			hoverAction = option;
			hoverNPC = target;
			//System.out.println("Hovered over NPC: " + target + " with option: " + option);
		} else {
			hoverNPC = null;
			hoverAction = null;
		}
	}

	private Rectangle getViewportBounds() {
		Widget viewport = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_INTERFACE_CONTAINER);
		if (viewport == null) {
			viewport = client.getWidget(WidgetInfo.FIXED_VIEWPORT);
		}

		if (viewport != null && !viewport.isHidden()) {
			return viewport.getBounds();
		}

		return new Rectangle(0, 0, 0, 0); // Return empty if no viewport found
	}

	private boolean isNpcFullyVisible(NPC npc, List<Rectangle> uiElements, Rectangle viewportBounds) {
		Polygon npcPoly = Perspective.getCanvasTilePoly(client, npc.getLocalLocation());
		if (npcPoly == null) {
			return false;
		}

		Rectangle npcBounds = npcPoly.getBounds();

		// Ensure NPC is inside the game viewport
		if (!viewportBounds.contains(npcBounds)) {
			return false;
		}

		// Ensure NPC is NOT inside any UI elements
		for (Rectangle uiBox : uiElements) {
			if (uiBox.intersects(npcBounds)) {
				return false;
			}
		}

		return true;
	}

	private List<Rectangle> getUIElementBounds() {
		List<Rectangle> uiElements = new ArrayList<>();

		// Add chatbox bounds
		Widget chatbox = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
		if (chatbox != null && !chatbox.isHidden()) {
			uiElements.add(chatbox.getBounds());
		}

		// Add inventory bounds
		Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
		if (inventory != null && !inventory.isHidden()) {
			uiElements.add(inventory.getBounds());
		}

		// Add minimap bounds
		Widget minimap = client.getWidget(10747996);
		if (minimap != null && !minimap.isHidden()) {
			uiElements.add(minimap.getBounds());
		}

		return uiElements;
	}



	private Point getNearestGoblin() {
		//set acceptable world points here based on the fight location
		acceptableWorldPoints.clear(); // Clear previous points
		for (int x = fightLocation.getX() - config.fightDistanceX(); x <= fightLocation.getX() + config.fightDistanceX(); x++) {
			for (int y = fightLocation.getY() - config.fightDistanceY(); y <= fightLocation.getY() + config.fightDistanceY(); y++) {
				WorldPoint targetLocation = new WorldPoint(x, y, fightLocation.getPlane());
				acceptableWorldPoints.add(targetLocation);
			}
		}

		List<NPC> npcs = client.getNpcs();

		Optional<NPC> nearestGoblin = npcs.stream()
				.filter(npc -> config.npcName().toLowerCase().equals(npc.getName().toLowerCase())) // Ensure name check is correct
				.filter(npc -> npc.getInteracting() == null) // Ignore goblins in combat
				.filter(npc -> npc.isDead() == false)
				.filter(npc -> acceptableWorldPoints.contains(npc.getWorldLocation()))
				.min(Comparator.comparingInt(npc ->
						client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation()))
				);

		if (!nearestGoblin.isPresent()) return null;
		NPC npc = nearestGoblin.get();

		if (npc.getConvexHull() == null || npc.getConvexHull().getBounds() == null)
			return null;

		return getRandomPointInBounds(npc.getConvexHull().getBounds());
	}

	public void sendMoveEvent(int x, int y) {
		Canvas canvas = client.getCanvas();
		java.awt.Point point = new java.awt.Point(x, y);
		SwingUtilities.convertPointToScreen(point, canvas);
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

	public void sendClickEventDelayed(int x, int y, int delay) {
		Timer timer = new Timer(delay, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// After 100ms, send the click event
				Canvas canvas = client.getCanvas();
				java.awt.Point point = new java.awt.Point(x, y);
				SwingUtilities.convertPointToScreen(point, canvas);

				// Press mouse button
				canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));
				// Release mouse button
				canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));
				// Click event (some cases require this)
				canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON1));

				// Stop the timer after the action is performed
				((Timer) e.getSource()).stop();
			}
		});

		timer.setRepeats(false); // Make sure it only fires once
		timer.start(); // Start the timer
	}

	private boolean isInventoryOpen() {
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
			return false;
		}

		return true;
	}

	private void checkCombatStatus() {
		Player player = client.getLocalPlayer();
		if (player == null) {
			inCombat = false;
			return;
		}

		// Check if the player is actively attacking an NPC
		if (player.getInteracting() instanceof NPC) {
			inCombat = true;
			return;
		}

		// Check if any NPC is targeting the player
		List<NPC> npcs = client.getNpcs();
		for (NPC npc : npcs) {
			if (npc.getInteracting() != null && npc.getInteracting().equals(player)) {
				inCombat = true;
				return;
			}
		}

		// If neither condition is met, the player is not in combat
		inCombat = false;
	}

	public boolean isInCombat() {
		return inCombat;
	}

	private void checkHealth() {
		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		lowHealth = currentHp <= (maxHp * (config.eatPercentage() / 100));
	}

	private void checkInventory() {
		hasHerring = client.getItemContainer(InventoryID.INVENTORY) != null &&
				client.getItemContainer(InventoryID.INVENTORY).contains(HERRING_ID);
	}

	public boolean isLowHealth() {
		return lowHealth;
	}

	public boolean hasHerring() {
		return hasHerring;
	}


	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
		}
	}

	@Provides
	GoblinTrainerPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GoblinTrainerPluginConfig.class);
	}
}
