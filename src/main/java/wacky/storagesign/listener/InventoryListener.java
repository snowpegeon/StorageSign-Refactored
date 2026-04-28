package wacky.storagesign.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockVector;
import org.bukkit.block.BlockFace;

import wacky.storagesign.ConfigLoader;
import wacky.storagesign.StorageSign;
import wacky.storagesign.StorageSignCore;
import wacky.storagesign.registry.MaterialRegistry;
import wacky.storagesign.task.ExportSignTask;

/**
 * Handles hopper-driven inventory transfers involving StorageSigns.
 *
 * <h3>Architecture</h3>
 * StorageSigns are sign blocks — they have no Bukkit {@link Inventory}. Hoppers therefore interact
 * with <em>adjacent containers</em> (chests, barrels, etc.) placed next to the SS block.
 * This listener scans the neighbouring blocks of each transfer's source and destination containers.
 *
 * <h3>Auto-import (SS absorbs items)</h3>
 * A hopper pushes items into a container that is adjacent to a matching SS. If the container
 * already holds a full stack, the moved item is absorbed directly into the SS instead (preventing
 * overflow). Runs inline so the inventory state is accurate.
 *
 * <h3>Auto-export (SS refills a container)</h3>
 * A hopper pulls items out of a container adjacent to a matching SS. A one-tick-deferred
 * {@link ExportSignTask} refills the container from the SS's stored amount, keeping it topped up.
 *
 * <h3>Inventory pickup</h3>
 * When a hopper picks up a dropped item entity, if the hopper (or an adjacent container) is next
 * to a matching SS and the hopper is already full of that item, the excess is absorbed into the SS.
 */
public final class InventoryListener implements Listener {

    private static final Logger LOG = Logger.getLogger(InventoryListener.class.getName());

    /** Directions to scan for adjacent StorageSigns. */
    private static final BlockFace[] SCAN_FACES = {
        BlockFace.UP, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST
    };

    private final StorageSignCore plugin;

    public InventoryListener(StorageSignCore plugin) {
        this.plugin = plugin;
    }

    // ── InventoryMoveItemEvent (hopper-to-container transfers) ────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        if (!ConfigLoader.getAutoImport() && !ConfigLoader.getAutoExport()) return;

        ItemStack item = event.getItem();

        // ── AUTO-IMPORT: destination container is adjacent to a matching SS ───
        // When a hopper pushes an item into the container, absorb excess into SS.
        if (ConfigLoader.getAutoImport()) {
            for (Block destHolder : holderBlocks(event.getDestination())) {
                Block ssBlock = findAdjacentSS(destHolder, item);
                if (ssBlock == null) continue;

                StorageSign ss = StorageSign.fromBlock(ssBlock);
                if (ss == null) break;

                // Absorb only when container already holds a full stack (prevent overflow)
                if (event.getDestination().containsAtLeast(item, item.getMaxStackSize())) {
                    event.getDestination().removeItem(item);
                    ss.setAmount(ss.getAmount() + item.getAmount());
                    updateSign(ssBlock, ss);
                    LOG.fine("Import: absorbed " + item.getAmount() + " into SS at " + ssBlock.getLocation());
                }
                break; // only process one SS per event
            }
        }

        // ── AUTO-EXPORT: source container is adjacent to a matching SS ─────────
        // When a hopper pulls an item out of the container, schedule a refill from SS.
        if (ConfigLoader.getAutoExport()) {
            for (Block srcHolder : holderBlocks(event.getSource())) {
                Block ssBlock = findAdjacentSS(srcHolder, item);
                if (ssBlock == null) continue;

                new ExportSignTask(ssBlock, event.getSource(), item.clone()).runTask(plugin);
                LOG.fine("Export: scheduled refill from SS at " + ssBlock.getLocation());
                break; // only schedule one task per event
            }
        }
    }

    // ── InventoryPickupItemEvent (hopper picks up a dropped item entity) ──────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!ConfigLoader.getAutoImport()) return;

        ItemStack item = event.getItem().getItemStack();

        for (Block holderBlock : holderBlocks(event.getInventory())) {
            Block ssBlock = findAdjacentSS(holderBlock, item);
            if (ssBlock == null) continue;

            StorageSign ss = StorageSign.fromBlock(ssBlock);
            if (ss == null) break;

            // Absorb if the inventory already holds a full stack (overflow prevention)
            if (event.getInventory().containsAtLeast(item, item.getMaxStackSize())) {
                event.getInventory().removeItem(item);
                ss.setAmount(ss.getAmount() + item.getAmount());
                updateSign(ssBlock, ss);
                LOG.fine("InventoryPickup absorbed: " + item.getAmount()
                         + " into SS at " + ssBlock.getLocation());
            }
            break;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Scans blocks adjacent to {@code container} (UP, S, N, E, W) for a StorageSign that
     * matches {@code item}.
     *
     * <ul>
     *   <li>UP direction: accepts only standing signs ({@code _SIGN} not {@code _WALL_SIGN}).</li>
     *   <li>Horizontal directions: accepts only wall signs whose facing matches the scan direction
     *       (i.e., the sign is attached to the container's face).</li>
     * </ul>
     *
     * @return the SS block, or {@code null} if none found.
     */
    static Block findAdjacentSS(Block container, ItemStack item) {
        for (int i = 0; i < SCAN_FACES.length; i++) {
            BlockFace face = SCAN_FACES[i];
            Block adjacent = container.getRelative(face);

            if (i == 0) {
                // UP: standing sign only
                if (!MaterialRegistry.SIGN_MATERIALS.contains(adjacent.getType())) continue;
            } else {
                // N/S/E/W: wall sign facing this direction (attached to container's face)
                if (!MaterialRegistry.WALL_SIGN_MATERIALS.contains(adjacent.getType())) continue;
                WallSign ws = (WallSign) adjacent.getBlockData();
                if (ws.getFacing() != face) continue;
            }

            StorageSign ss = StorageSign.fromBlock(adjacent);
            if (ss == null) continue;
            if (!ss.isSimilar(item)) continue;

            return adjacent;
        }
        return null;
    }

    /**
     * Returns the physical holder block(s) for an inventory.
     *
     * <ul>
     *   <li>Double chest → both halves.</li>
     *   <li>Minecart or null location → empty list (skip).</li>
     *   <li>Other BlockState → single-element list.</li>
     * </ul>
     */
    private static List<Block> holderBlocks(Inventory inventory) {
        if (inventory == null || inventory.getLocation() == null) return List.of();

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof StorageMinecart) return List.of(); // skip minecarts

        if (holder instanceof DoubleChest dc) {
            List<Block> result = new ArrayList<>(2);
            if (dc.getLeftSide()  instanceof BlockState bs) result.add(bs.getBlock());
            if (dc.getRightSide() instanceof BlockState bs) result.add(bs.getBlock());
            return result;
        }

        if (holder instanceof BlockState bs) return List.of(bs.getBlock());
        return List.of();
    }

    private static void updateSign(Block ssBlock, StorageSign ss) {
        if (ssBlock.getState() instanceof Sign sign) {
            ss.applyToSign(sign);
        }
    }
}
