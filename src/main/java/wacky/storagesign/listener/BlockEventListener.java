package wacky.storagesign.listener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.sign.Side;

import wacky.storagesign.ConfigLoader;
import wacky.storagesign.StorageSign;
import wacky.storagesign.StorageSignCore;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Handles block-level events for StorageSigns:
 * <ul>
 *   <li>Dropping the sign item with lore when a StorageSign is broken</li>
 *   <li>Restoring the sign block when a StorageSign item is placed</li>
 *   <li>Preventing manual sign-text edits that would corrupt the data</li>
 * </ul>
 */
public final class BlockEventListener implements Listener {

    private static final Logger LOG = Logger.getLogger(BlockEventListener.class.getName());
    private static final BlockFace[] SCAN_FACES = {
        BlockFace.UP, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST
    };

    public BlockEventListener(StorageSignCore plugin) {
    }

    // ── BlockBreakEvent ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("storagesign.break")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
            return;
        }

        if (StorageSign.isStorageSign(event.getBlock())) {
            event.setDropItems(false);
        }

        dropRelativeSigns(event.getBlock());
    }

    // ── BlockPlaceEvent ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        StorageSign ss = StorageSign.fromItemStack(item);
        if (ss == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("storagesign.place")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
            return;
        }

        Block placed = event.getBlockPlaced();
        if (!(placed.getState() instanceof Sign sign)) return;

        // Restore the sign text from item lore
        ss.applyToSign(sign);
        if (item.getType() == Material.DARK_OAK_SIGN) {
            sign.getSide(Side.FRONT).setColor(DyeColor.WHITE);
            sign.update();
        }
        // Close inventory to prevent UI timing glitch when placing from inventory (matches original)
        player.closeInventory();
        LOG.fine("Restored StorageSign block at " + placed.getLocation());
    }

    // ── SignChangeEvent ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;

        // If this sign is already a StorageSign, cancel the edit
        if (StorageSign.isStorageSign(block)) {
            if (block.getState() instanceof Sign sign) {
                String[] lines = sign.getSide(Side.FRONT).getLines();
                for (int i = 0; i < 4 && i < lines.length; i++) {
                    event.setLine(i, lines[i]);
                }
                sign.update();
            } else {
                event.setCancelled(true);
            }
            return;
        }

        String firstLine = event.getLine(0);
        if (firstLine == null || !firstLine.equalsIgnoreCase("storagesign")) return;

        if (event.getPlayer().hasPermission("storagesign.create")) {
            event.setLine(0, "StorageSign");
            return;
        }

        event.getPlayer().sendMessage("§c" + ConfigLoader.getNoPermission());
        event.setCancelled(true);
    }

    // ── Shared helper ──────────────────────────────────────────────────────────

    /**
     * Drops StorageSign data for the target block and adjacent attached StorageSigns.
     */
    public static void dropRelativeSigns(Block block) {
        Map<Block, StorageSign> breakSignMap = new LinkedHashMap<>();
        collectStorageSign(block, breakSignMap);

        for (int i = 0; i < SCAN_FACES.length; i++) {
            BlockFace face = SCAN_FACES[i];
            Block relBlock = block.getRelative(face);

            if (i == 0) {
                if (!MaterialRegistry.SIGN_MATERIALS.contains(relBlock.getType())) continue;
                collectStorageSign(relBlock, breakSignMap);
            } else {
                if (!MaterialRegistry.WALL_SIGN_MATERIALS.contains(relBlock.getType())) continue;
                WallSign wallSign = (WallSign) relBlock.getBlockData();
                if (wallSign.getFacing() != face) continue;
                collectStorageSign(relBlock, breakSignMap);
            }
        }

        for (Map.Entry<Block, StorageSign> entry : breakSignMap.entrySet()) {
            dropSingleStorageSign(entry.getKey(), entry.getValue());
        }
    }

    private static void collectStorageSign(Block block, Map<Block, StorageSign> result) {
        if (result.containsKey(block)) return;
        StorageSign ss = StorageSign.fromBlock(block);
        if (ss == null) return;
        result.put(block, ss);
    }

    private static void dropSingleStorageSign(Block signBlock, StorageSign ss) {
        Material itemMat = MaterialRegistry.WALL_TO_SIGN.getOrDefault(signBlock.getType(), signBlock.getType());
        ItemStack drop = StorageSign.createStorageSignItem(itemMat, ss.getLoreText(), 1);

        Location dropLocation = signBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        signBlock.getWorld().dropItem(dropLocation, drop);
        signBlock.setType(Material.AIR);
        LOG.fine("Dropped StorageSign item at " + signBlock.getLocation());
    }
}
