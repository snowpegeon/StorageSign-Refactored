package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

import wacky.storagesign.ConfigLoader;
import wacky.storagesign.StorageSign;

/**
 * Handles entity-level events relevant to StorageSigns:
 * <ul>
 *   <li>Players picking up dropped items → auto-import into a held StorageSign item</li>
 *   <li>Block changes by entities (e.g. gravel/sand falling) → drop SS item if a sign is broken</li>
 * </ul>
 */
public final class EntityListener implements Listener {

    private static final Logger LOG = Logger.getLogger(EntityListener.class.getName());

    // ── EntityPickupItemEvent ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && ConfigLoader.getAutocollect()) {
            Player player = (Player) event.getEntity();
            if (!player.hasPermission("storagesign.autocollect")) return;

            PlayerInventory inv = player.getInventory();
            ItemStack picked = event.getItem().getItemStack();

            // Main hand first (original behavior), then off-hand.
            if (autoCollectToHand(inv.getItemInMainHand(), picked, inv, event)) {
                inv.setItemInMainHand(
                    updatedStorageSign(inv.getItemInMainHand(), picked.getAmount())
                );
                player.updateInventory();
                return;
            }
            if (autoCollectToHand(inv.getItemInOffHand(), picked, inv, event)) {
                inv.setItemInOffHand(
                    updatedStorageSign(inv.getItemInOffHand(), picked.getAmount())
                );
                player.updateInventory();
                return;
            }
        }

        // Prevent non-player entities from picking up StorageSign items.
        if (event.getEntityType() != EntityType.PLAYER) {
            ItemStack stack = event.getItem().getItemStack();
            if (StorageSign.isStorageSign(stack)) {
                event.getItem().setPickupDelay(20);
                event.setCancelled(true);
            }
        }
    }

    private static boolean autoCollectToHand(ItemStack handSSItem, ItemStack picked, PlayerInventory inv,
                                             EntityPickupItemEvent event) {
        StorageSign ss = StorageSign.fromItemStack(handSSItem);
        if (ss == null || ss.isEmpty()) return false;
        if (handSSItem.getAmount() != 1) return false;
        if (!ss.isSimilar(picked)) return false;
        if (!inv.containsAtLeast(picked, picked.getMaxStackSize())) return false;

        event.setCancelled(true);
        event.getItem().remove();
        return true;
    }

    private static ItemStack updatedStorageSign(ItemStack handSSItem, int addAmount) {
        StorageSign ss = StorageSign.fromItemStack(handSSItem);
        if (ss == null) return handSSItem;
        ss.setAmount(ss.getAmount() + addAmount);
        return StorageSign.createStorageSignItem(handSSItem.getType(), ss.getLoreText(), handSSItem.getAmount());
    }

    // ── EntityChangeBlockEvent ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!ConfigLoader.getFallingBlockItemSS()) return;
        if (!(event.getEntity() instanceof FallingBlock)) return;

        Block block = event.getBlock();
        BlockEventListener.dropRelativeSigns(block);
        LOG.fine("EntityChangeBlock: dropped adjacent StorageSign(s) at " + block.getLocation());
    }
}
