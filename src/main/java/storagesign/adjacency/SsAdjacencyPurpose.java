package storagesign.adjacency;

/**
 * 隣接判定の利用目的。
 */
public enum SsAdjacencyPurpose {
    /** InventoryMoveItemEvent / InventoryPickupItemEvent の照合。 */
    INVENTORY_TRANSFER,

    /** 破壊時に支持先ブロックへ付随する看板を回収する照合。 */
    ATTACHED_SIGN_DROP
}
