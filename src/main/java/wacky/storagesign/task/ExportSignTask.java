package wacky.storagesign.task;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import wacky.storagesign.StorageSign;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * One-shot {@link BukkitRunnable} that refills a container from an adjacent StorageSign.
 *
 * <p>Runs on the tick <em>after</em> a hopper pulls an item from the container, so the inventory
 * state is stable and reflects what the hopper actually took.
 *
 * <h3>Refill logic</h3>
 * <ol>
 *   <li>Re-read the SS from the sign block (always uses current state).</li>
 *   <li>If the source container still holds a full stack of the item, no refill is needed.</li>
 *   <li>Otherwise calculate how many items to add to bring existing stacks back to capacity.</li>
 *   <li>Use {@link Inventory#addItem} so the server handles slot selection correctly.</li>
 *   <li>Deduct only the amount that was actually added from the SS's stored count.</li>
 * </ol>
 *
 * <h3>Why one tick later?</h3>
 * In both Spigot and Paper, {@code InventoryMoveItemEvent} fires <em>before</em> the item moves.
 * If we refilled the container immediately, we would refill before the hopper had taken anything.
 * Deferring ensures the item is gone and the container is "short" before we top it up.
 */
public final class ExportSignTask extends BukkitRunnable {

    private static final Logger LOG = Logger.getLogger(ExportSignTask.class.getName());

    /** The StorageSign block to refill from. */
    private final Block ssBlock;

    /** The container adjacent to the SS whose items are being pulled by the hopper. */
    private final Inventory sourceInventory;

    /** Clone of the moved item — used to find and count matching slots. */
    private final ItemStack movedItem;

    /**
     * @param ssBlock         the StorageSign block adjacent to the source container
     * @param sourceInventory the container inventory being pulled from by the hopper
     * @param movedItem       a clone of {@code event.getItem()} for reference type matching
     */
    public ExportSignTask(Block ssBlock, Inventory sourceInventory, ItemStack movedItem) {
        this.ssBlock         = ssBlock;
        this.sourceInventory = sourceInventory;
        this.movedItem       = movedItem;
    }

    @Override
    public void run() {
        // Re-read sign state at task execution time (important: always fresh read)
        StorageSign ss = StorageSign.fromBlock(ssBlock);
        if (ss == null || ss.isEmpty() || ss.getAmount() <= 0) return;

        int maxStack = movedItem.getMaxStackSize();

        // If source still contains a full stack, no refill needed
        if (sourceInventory.containsAtLeast(movedItem, maxStack)) return;
        if (ss.getAmount() < movedItem.getAmount()) return;

        // Count how many matching items are currently in the source container
        int stacks = 0;
        int total  = 0;
        for (ItemStack slot : sourceInventory.getContents()) {
            if (slot != null && ss.isSimilar(slot)) {
                stacks++;
                total += slot.getAmount();
            }
        }

        // If all matching slots are completely full and no empty slot exists, nothing to add
        if (total == stacks * maxStack && sourceInventory.firstEmpty() == -1) return;

        // Calculate how many items to add: fill existing partial stacks to their max
        int addAmount = (stacks == 0) ? maxStack : (maxStack * stacks - total);
        addAmount = Math.min(addAmount, ss.getAmount());
        if (addAmount <= 0) return;

        // Keep original behavior: refill item template comes from the moved stack itself.
        ItemStack refill = movedItem.clone();
        refill.setAmount(addAmount);
        if (!ss.isSimilar(refill)) return;

        int actualAdded = addToSource(refill, addAmount);

        if (actualAdded <= 0) return;

        ss.setAmount(ss.getAmount() - actualAdded);
        if (ssBlock.getState() instanceof Sign sign) {
            ss.applyToSign(sign);
        }
        LOG.fine("ExportSignTask: refilled " + actualAdded + " into container from SS at "
                 + ssBlock.getLocation() + ", remaining=" + ss.getAmount());
    }

    private int addToSource(ItemStack refill, int addAmount) {
        InventoryType type = sourceInventory.getType();
        if (type == InventoryType.BREWING) {
            return addToBrewing(refill, addAmount);
        }
        if (type == InventoryType.FURNACE
            || type == InventoryType.BLAST_FURNACE
            || type == InventoryType.SMOKER) {
            return addToFurnace(refill, addAmount);
        }
        var leftover = sourceInventory.addItem(refill);
        return addAmount - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private int addToBrewing(ItemStack refill, int maxAdd) {
        Material type = refill.getType();
        if (type == Material.BLAZE_POWDER) {
            return addIntoSlot(4, refill, maxAdd);
        }
        if (MaterialRegistry.POTION_MATERIALS.contains(type)) {
            int remaining = maxAdd;
            int added = 0;
            for (int slot = 0; slot < 3 && remaining > 0; slot++) {
                int oneAdded = addIntoSlot(slot, refill, remaining);
                remaining -= oneAdded;
                added += oneAdded;
            }
            return added;
        }
        if (!isBrewingIngredient(type)) return 0;
        return addIntoSlot(3, refill, maxAdd);
    }

    private int addToFurnace(ItemStack refill, int maxAdd) {
        int slot = refill.getType().isFuel() ? 1 : 0;
        return addIntoSlot(slot, refill, maxAdd);
    }

    private int addIntoSlot(int slot, ItemStack template, int maxAdd) {
        ItemStack existing = sourceInventory.getItem(slot);
        int stackMax = template.getMaxStackSize();
        if (existing == null || existing.getType() == Material.AIR) {
            int add = Math.min(maxAdd, stackMax);
            ItemStack inserted = template.clone();
            inserted.setAmount(add);
            sourceInventory.setItem(slot, inserted);
            return add;
        }
        if (!existing.isSimilar(template)) return 0;
        int space = stackMax - existing.getAmount();
        if (space <= 0) return 0;
        int add = Math.min(maxAdd, space);
        existing.setAmount(existing.getAmount() + add);
        sourceInventory.setItem(slot, existing);
        return add;
    }

    private static boolean isBrewingIngredient(Material material) {
        return switch (material) {
            case NETHER_WART, SUGAR, REDSTONE, GLOWSTONE_DUST, GUNPOWDER,
                 RABBIT_FOOT, GLISTERING_MELON_SLICE, GOLDEN_CARROT, MAGMA_CREAM,
                 GHAST_TEAR, SPIDER_EYE, FERMENTED_SPIDER_EYE, DRAGON_BREATH,
                 PUFFERFISH, TURTLE_HELMET, PHANTOM_MEMBRANE, BREEZE_ROD -> true;
            default -> false;
        };
    }
}
