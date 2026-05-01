package storagesign.adjacency;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * 隣接 StorageSign 判定の入力。
 */
public record SsAdjacencyQuery(Block containerBlock, ItemStack item, SsAdjacencyPurpose purpose) {

    public SsAdjacencyQuery {
        if (containerBlock == null) {
            throw new IllegalArgumentException("containerBlock must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose must not be null");
        }
    }
}
