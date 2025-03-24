package net.runelite.client.plugins.aiofighter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
	name = "AIO Fighter"
)
public class AIOFighterPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private AIOFighterConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private AIOFighterOverlay overlay;

	private volatile boolean running = true;
	private boolean lowHealth = false;
	private boolean hasFood = false;
	private boolean inCombat = false;
	private String hoverNPC = null;
	private String hoverAction = null;
	private static final Random random = new Random();
	private Set<WorldPoint> acceptableWorldPoints = new HashSet<>();
	public WorldPoint fightLocation = null;
	private ScheduledExecutorService scheduler;
	private Thread loopThread;

	@Override
	protected void startUp() throws Exception
	{
		log.info("AIO Fighter started!");
		setCameraPitch();

		if (client != null && client.getLocalPlayer() != null) {
			fightLocation = client.getLocalPlayer().getWorldLocation();
		}

		overlayManager.add(overlay);

		running = true;
		scheduler = Executors.newSingleThreadScheduledExecutor(); // Create a single reusable scheduler

		loopThread = new Thread(this::loop, "LoopingPlugin-Thread");
		loopThread.start();
	}

	@Override
	protected void shutDown() throws Exception {
		running = false;
		if (loopThread != null) {
			loopThread.interrupt();
			loopThread = null;
		}

		if (scheduler != null) {
			scheduler.shutdownNow(); // Properly shutdown the scheduler to prevent lingering tasks
			scheduler = null;
		}

		overlayManager.remove(overlay);

		log.info("AIO Fighter stopped!");
	}

	private void loop() {
		while (running) {
			CountDownLatch latch = new CountDownLatch(1);

			clientThread.invokeLater(() -> {
				if (client != null && running && !config.isPaused()) {
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
							if (hasFood) {
								point = getFoodLocation();
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
							Point point = getNearestNPC();
							if (point != null) {
								//System.out.println(hoverAction);
								//System.out.println(hoverNPC);
								sendMoveEvent(point.getX(), point.getY());

								if (scheduler == null || scheduler.isShutdown()) {
									return; // Avoid running if the plugin is stopped
								}

								scheduler.schedule(() -> {
									clientThread.invokeLater(() -> { // Ensure game actions run on the client thread
										MenuEntry[] menuEntries = client.getMenuEntries();

										if (menuEntries != null && menuEntries.length > 0) {
											// Sort by MenuAction ID (smallest to largest)
											Arrays.sort(menuEntries, Comparator.comparingInt(entry -> entry.getType().getId()));

											// Get the first menu entry after sorting
											MenuEntry firstEntry = menuEntries[0];

											if (firstEntry.getTarget() != null && firstEntry.getOption() != null) {
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
											}
										}
									});
								}, 100, TimeUnit.MILLISECONDS);
							}
						}
					}
				}
				latch.countDown(); // Signal loop to continue
			});

			try {
				latch.await(); // Wait until clientThread task completes
				Thread.sleep(1000); // Enforce 1-second gap before next iteration
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private Point getFoodLocation() {
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
			return null;
		}

		for (Widget item : inventoryWidget.getDynamicChildren()) {
			if (item.getItemId() == config.foodID() || item.getId() == config.foodID()) {
				return getRandomPointInBounds(item.getBounds());
			}
		}

		return null;
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

	private boolean isInventoryOpen() {
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
			return false;
		}

		return true;
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

	private Point getNearestNPC() {
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

	private void checkInventory() {
		if (client.getItemContainer(InventoryID.INVENTORY) != null) {
			for (var item : client.getItemContainer(InventoryID.INVENTORY).getItems()) {
				if (item.getId() == config.foodID()) {
					hasFood = true;
					return;
				}
			}
		}
		hasFood = false;
	}

	private void checkHealth() {
		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		lowHealth = currentHp <= (maxHp * (config.eatPercentage() / 100));
	}

	public void setCameraPitch() {
		int pitch = client.getCameraPitch();
		if (pitch >= 0 && pitch <= 383) {  // Validate pitch range (check if 383 is valid within the game)
			client.setCameraPitchTarget(383);
		} else {
			System.out.println("Invalid pitch value. Must be between 0 and 383.");
		}
	}

	@Provides
	AIOFighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AIOFighterConfig.class);
	}
}
