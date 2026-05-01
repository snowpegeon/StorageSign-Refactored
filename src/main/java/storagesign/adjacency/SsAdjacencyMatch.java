package storagesign.adjacency;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import storagesign.StorageSign;

/**
 * 隣接判定で見つかった StorageSign の実体。
 */
public record SsAdjacencyMatch(Block signBlock, Sign signState, StorageSign storageSign) {
}
