package net.runelite.client.plugins.aiofighter;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.RSTimeUnit;

import javax.inject.Inject;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

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
	@Inject
	private AIOFighterMouseOverlay mouseOverlay;
	@Inject
	private ItemManager itemManager;

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
	private Table<WorldPoint, Integer, GroundItem> collectedGroundItems = HashBasedTable.create();
	Point mouseLocation = null;

	@Override
	protected void startUp() throws Exception {
		log.info("AIO Fighter started!");
		setCameraPitch();

		if (client != null && client.getLocalPlayer() != null) {
			fightLocation = client.getLocalPlayer().getWorldLocation();
		}

		overlayManager.add(overlay);
		overlayManager.add(mouseOverlay);

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
		overlayManager.remove(mouseOverlay);

		log.info("AIO Fighter stopped!");
	}

	private void loop() {
		while (running) {
			CountDownLatch latch = new CountDownLatch(1);

			if (client != null && running && !config.isPaused()) {
				checkBasicStats();
				clickToContinue();

				if (lowHealth) {
					if (lowHealthLogic()) {
						return;
					}
				} else {
					if (inCombat || isPlayerInteracting()) {
						//do nothing and keep attacking
					} else {
						if (config.shouldPickUpBigBones()) {
							List<WorldPoint> boneLocations = new ArrayList<>();
							for (var set : collectedGroundItems.cellSet()) {
								if (set.getValue().getName().toLowerCase().contains("big bones")) {
									boneLocations.add(set.getRowKey());
								}
							}


							int emptyInventoryCount = getEmptyInventorySlotsClientThread();

							if (emptyInventoryCount == 0) {
								// Check if we have bones in inventory to bury
								//open inventory
								Point boneInventoryLocation = null;
								try {
									boneInventoryLocation = getBigBoneInventoryLocationClientThread();
								} catch (Exception ex) { }
								while (boneInventoryLocation != null) {
									sendMoveAndClick(boneInventoryLocation.getX(), boneInventoryLocation.getY());

									//wait for the animation - 1200-2000 ms
									try {
										Thread.sleep(getRandomBetween(1200, 2000));
									} catch (InterruptedException e) {
									}

									//check agian if we have any bones to bury
									boneInventoryLocation = getBigBoneInventoryLocationClientThread();
								}

							} else {
								while (!boneLocations.isEmpty() && getEmptyInventorySlotsClientThread() > 0) {
									System.out.println(1007);
									//get the closest bone
									int closestBoneIndex = 0;
									try {
										closestBoneIndex = getClosestBone(boneLocations);
									} catch (Exception ex) { }

									Point point = null;
									try {
										point = getPointFromWorldPoint(boneLocations.get(closestBoneIndex));
									} catch (Exception ex) { }

									if (point != null) {
										sendMoveEvent(point.getX(), point.getY());
										try {
											Thread.sleep(getRandomBetween(100, 200));
										} catch (Exception ex) { }

										// Check if left-click is "Take Big Bones"
										String[] leftClickOption = getLeftClickOption();
										//System.out.println(leftClickOption[0] + ", " + leftClickOption[1]);
										if (leftClickOption == null || leftClickOption[0] == null || leftClickOption[1] == null || !leftClickOption[0].equalsIgnoreCase("take") || !leftClickOption[1].toLowerCase().contains("bones")) {
											System.out.println("Left-click is not 'Take Big Bones'. Right-clicking...");
											sendRightClick(point.getX(), point.getY());

											try {
												Thread.sleep(getRandomBetween(100, 200)); // Wait for menu
											} catch (InterruptedException e) {
											}

											clickMenuOption("take", "bones");
										} else {
											sendClickEvent(point.getX(), point.getY()); // Normal left-click pickup
										}

										try {
											Thread.sleep(getRandomBetween(2000, 3000));
										} catch (InterruptedException e) {
										}
									}

									boneLocations = new ArrayList<>();
									for (var set : collectedGroundItems.cellSet()) {
										if (set.getValue().getName().toLowerCase().contains("big bones")) {
											boneLocations.add(set.getRowKey());
										}
									}
								}
							}
						}

						//find a goblin and send the coords
						findAndKillNPCClientThread();
						if (config.shouldPickUpBigBones()) {
							try {
								Thread.sleep(getRandomBetween(1200, 1500));
							} catch (InterruptedException e) { }
						}
					}
				}
			}
			latch.countDown();
			try {
				latch.await();
				Thread.sleep(600);
			} catch (InterruptedException e) { }
		}
	}

	private GroundItem buildGroundItem(final Tile tile, final TileItem item)
	{
		// Collect the data for the item
		final int itemId = item.getId();
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int realItemId = itemComposition.getNote() != -1 ? itemComposition.getLinkedNoteId() : itemId;
		final int alchPrice = itemComposition.getHaPrice();
		final int despawnTime = item.getDespawnTime() - client.getTickCount();
		final int visibleTime = item.getVisibleTime() - client.getTickCount();

		final GroundItem groundItem = GroundItem.builder()
				.id(itemId)
				.location(tile.getWorldLocation())
				.itemId(realItemId)
				.quantity(item.getQuantity())
				.name(itemComposition.getName())
				.haPrice(alchPrice)
				.height(tile.getItemLayer().getHeight())
				.tradeable(itemComposition.isTradeable())
				.ownership(item.getOwnership())
				.isPrivate(item.isPrivate())
				.spawnTime(Instant.now())
				.stackable(itemComposition.isStackable())
				.despawnTime(Duration.of(despawnTime, RSTimeUnit.GAME_TICKS))
				.visibleTime(Duration.of(visibleTime, RSTimeUnit.GAME_TICKS))
				.build();

		// Update item price in case it is coins
		if (realItemId == 995)
		{
			groundItem.setHaPrice(1);
			groundItem.setGePrice(1);
		}
		else
		{
			groundItem.setGePrice(itemManager.getItemPrice(realItemId));
		}

		return groundItem;
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		if (acceptableWorldPoints.isEmpty()) {
			//set acceptable world points here based on the fight location
			acceptableWorldPoints.clear(); // Clear previous points
			for (int x = fightLocation.getX() - config.fightDistanceX(); x <= fightLocation.getX() + config.fightDistanceX(); x++) {
				for (int y = fightLocation.getY() - config.fightDistanceY(); y <= fightLocation.getY() + config.fightDistanceY(); y++) {
					WorldPoint targetLocation = new WorldPoint(x, y, fightLocation.getPlane());
					acceptableWorldPoints.add(targetLocation);
				}
			}
		}


		TileItem item = itemSpawned.getItem();
		Tile tile = itemSpawned.getTile();

		GroundItem groundItem = buildGroundItem(tile, item);
		GroundItem existing = collectedGroundItems.get(tile.getWorldLocation(), item.getId());
		if (existing != null)
		{
			existing.setQuantity(existing.getQuantity() + groundItem.getQuantity());
			// The spawn time remains set at the oldest spawn
			existing.reset();
		}
		else
		{
			if (acceptableWorldPoints.contains(tile.getWorldLocation())) {
				collectedGroundItems.put(tile.getWorldLocation(), item.getId(), groundItem);
			}
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		TileItem item = itemDespawned.getItem();
		Tile tile = itemDespawned.getTile();

		GroundItem groundItem = collectedGroundItems.get(tile.getWorldLocation(), item.getId());
		if (groundItem == null)
		{
			return;
		}

		if (groundItem.getQuantity() <= item.getQuantity())
		{
			if (collectedGroundItems.contains(tile.getWorldLocation(), item.getId())) {
				collectedGroundItems.remove(tile.getWorldLocation(), item.getId());
			}
		}
		else
		{
			groundItem.setQuantity(groundItem.getQuantity() - item.getQuantity());
			// When picking up an item when multiple stacks appear on the ground,
			// it is not known which item is picked up, so we invalidate the spawn
			// time
			groundItem.setSpawnTime(null);
			groundItem.reset();
		}
	}

	private String[] getLeftClickOption() {
		CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[2];
		clientThread.invokeLater(() -> {
			MenuEntry[] menuEntries = client.getMenuEntries();
			if (menuEntries.length > 0) {
				result[0] = menuEntries[menuEntries.length - 1].getOption();
				result[1] = menuEntries[menuEntries.length - 1].getTarget();
			} else {
				result[0] = null;
			}

			latch.countDown();
		});
		try {
			latch.await();
		} catch (InterruptedException e) { }
		return result;
	}

	private void clickMenuOption(String option, String target) {
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invokeLater(() -> {
			MenuEntry[] menuEntries = client.getMenuEntries();
			for (MenuEntry entry : menuEntries) {
				if (entry.getOption().equalsIgnoreCase(option) && entry.getTarget().toLowerCase().contains(target)) {
					client.menuAction(
							entry.getParam0(),
							entry.getParam1(),
							entry.getType(),
							entry.getIdentifier(),
							entry.getItemId(),
							entry.getOption(),
							entry.getTarget()
					);
					sendMoveEvent(0, 0);
					//client.setMenuEntries(new MenuEntry[]{});
					break;
				}
			}
			latch.countDown();
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void sendRightClick(int x, int y) {
		Canvas canvas = client.getCanvas();
		java.awt.Point point = new java.awt.Point(x, y);
		SwingUtilities.convertPointToScreen(point, canvas);

		// Press right mouse button
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON3_DOWN_MASK, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON3));
		// Release right mouse button
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON3));
		// Right-click event (some cases require this)
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, point.x, point.y, 1, false, MouseEvent.BUTTON3));
	}

	private void findAndKillNPCClientThread() {
		clientThread.invokeLater(() -> {
			Point point = getNearestNPC();
			if (point != null && client.getLocalPlayer().isInteracting() == false) {
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
		});
	}

	private Point getPointFromWorldPoint(WorldPoint worldPoint) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		final Point[] result = new Point[1];

		clientThread.invokeLater(() -> {
			LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
			if (localPoint != null) {
				Point point = Perspective.localToCanvas(client, localPoint, worldPoint.getPlane());
				if (point != null) {
					result[0] = point;
				} else {
					result[0] = null;
				}
			} else {
				result[0] = null;
			}

			latch.countDown();
		});

		latch.await();
		return result[0];

	}

	private int getClosestBone(List<WorldPoint> boneLocations) throws InterruptedException {
		//set acceptable world points here based on the fight location
		acceptableWorldPoints.clear(); // Clear previous points
		for (int x = fightLocation.getX() - config.fightDistanceX(); x <= fightLocation.getX() + config.fightDistanceX(); x++) {
			for (int y = fightLocation.getY() - config.fightDistanceY(); y <= fightLocation.getY() + config.fightDistanceY(); y++) {
				WorldPoint targetLocation = new WorldPoint(x, y, fightLocation.getPlane());
				acceptableWorldPoints.add(targetLocation);
			}
		}

		CountDownLatch latch = new CountDownLatch(1);
		final int[] result = new int[1];
		clientThread.invokeLater(() -> {
			for (int i = 0; i < boneLocations.size(); i++) {
				double distance = client.getLocalPlayer().getWorldLocation().distanceTo(boneLocations.get(i));
				if (distance < client.getLocalPlayer().getWorldLocation().distanceTo(boneLocations.get(result[0]))) {
					if (acceptableWorldPoints.contains(boneLocations.get(i))) {
						result[0] = i;
					}
				}
			}

			latch.countDown();
		});

		latch.await();
		return result[0];
	}

	public int getRandomBetween(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	private Point getBigBoneInventoryLocationClientThread() {
		final Point[] points = new Point[1];
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invokeLater(() -> {
			Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
			if (inventoryWidget == null || inventoryWidget.getDynamicChildren() == null || inventoryWidget.isHidden()) {
				points[0] = null;
			} else {
				boolean found = false;
				for (Widget item : inventoryWidget.getDynamicChildren()) {
					if (item != null && (item.getItemId() == 532 || item.getId() == 532)) {
						points[0] = getRandomPointInBounds(item.getBounds());
						found = true;
						break;
					}
				}

				if (!found) {
					points[0] = null;
				}

			}

			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return points[0];
	}

	private int getEmptyInventorySlotsClientThread() {
		CountDownLatch latch = new CountDownLatch(1);
		final int[] result = new int[1];

		clientThread.invokeLater(() -> {
			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			if (inventory == null) {
				result[0] = 0;
			} else {
				int emptyCount = 0;
				Item[] items = inventory.getItems();

				for (int i = 0; i < 28; i++) {
					if (i >= items.length || items[i] == null || items[i].getId() == -1) {
						emptyCount++;
					}
				}

				result[0] = emptyCount;
			}

			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) { }
		return result[0];
	}

	private void checkBasicStats() {
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invokeLater(() -> {
			checkHealth();
			checkInventory();
			checkCombatStatus();

			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void clickToContinue() {
		CountDownLatch latch = new CountDownLatch(1);
		clientThread.invokeLater(() -> {
			//make sure we hit click to continue if it exists
			Widget clickToContinue = client.getWidget(15269891);
			if (clickToContinue != null && clickToContinue.isHidden() == false) {
				//System.out.println("test");
				Point point = getRandomPointInBounds(clickToContinue.getBounds());
				if (point != null) {
					sendMoveAndClick(point.getX(), point.getY());
				}
			}

			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean lowHealthLogic() {
		CountDownLatch latch = new CountDownLatch(1);
		final boolean[] result = new boolean[1];
		clientThread.invokeLater(() -> {
			if (!inCombat) {
				Point logoutButtonLocation = getLogoutButtonLocation();
				if (logoutButtonLocation != null) {
					sendMoveAndClick(logoutButtonLocation.getX(), logoutButtonLocation.getY());
					return true;
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

			latch.countDown();
			return false;
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return false;
	}

	private boolean isPlayerInteracting() {
		CountDownLatch latch = new CountDownLatch(1);
		final boolean[] result = new boolean[1];

		clientThread.invokeLater(() -> {
			result[0] = client.getLocalPlayer().isInteracting();

			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return result[0];
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
		mouseLocation = new Point(x, y);
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
