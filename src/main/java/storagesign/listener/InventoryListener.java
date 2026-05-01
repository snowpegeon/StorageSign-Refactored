package storagesign.listener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockFace;

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignCore;
import storagesign.registry.MaterialRegistry;
import storagesign.task.ExportSignTask;

/**
 * StorageSign を含むホッパー駆動のインベントリ移送を処理する。
 *
 * <h3>アーキテクチャ</h3>
 * StorageSign は看板ブロックであり Bukkit {@link Inventory} を持たない。
 * ホッパーは SS ブロックに隣接するコンテナ（チェスト・樽等）とやり取りする。
 * このリスナーは各転送の送信元・受信先コンテナの隣接ブロックをスキャンする。
 *
 * <h3>自動インポート (SS がアイテムを吸収)</h3>
 * ホッパーが SS に隣接するコンテナにアイテムを投入する際、コンテナが満杯ならインベントリオーバーフローを防ぐため
 * SS に吸収する。インベントリ状態が正確なようインライン実行する。
 *
 * <h3>自動エクスポート (SS がコンテナを補充)</h3>
 * ホッパーが SS 隣接コンテナからアイテムを引き出すとき、1 ティック遅延の
 * {@link ExportSignTask} が SS の保管数量からコンテナを補充する。
 *
 * <h3>インベントリピックアップ</h3>
 * ホッパーが落としたアイテムエンティティを拾う場合、隣接 SS のアイテムと一致し
 * ホッパーが満杯なら余剰分を SS に吸収する。
 */
public final class InventoryListener implements Listener {

    private static final Logger LOG = Logger.getLogger(InventoryListener.class.getName());

    /** 隣接 StorageSign をスキャンする方向。 */
    private static final BlockFace[] SCAN_FACES = {
        BlockFace.UP, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST
    };

    /** 隣接 SS スキャン結果を 1 つにまとめ、ブロック状態の再取得を回避する。 */
    private record SSMatch(Block block, Sign sign, StorageSign ss) {}

    private final StorageSignCore plugin;

    /** このティックで ExportSignTask が既にスケジュール済みの SS ブロックを追跡する。 */
    private final Set<Block> pendingExports = new HashSet<>();

    public InventoryListener(StorageSignCore plugin) {
        this.plugin = plugin;
    }

    // ── InventoryMoveItemEvent（ホッパー→コンテナ移送）──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        boolean autoImport = ConfigLoader.getAutoImport();
        boolean autoExport = ConfigLoader.getAutoExport();
        if (!autoImport && !autoExport) return;

        ItemStack item = event.getItem();
        if (item == null || item.getAmount() <= 0) return;

        int maxStack = item.getMaxStackSize();

        // ── AUTO-IMPORT: destination container is adjacent to a matching SS ───
        // When a hopper pushes an item into the container, absorb excess into SS.
        if (autoImport) {
            Inventory destination = event.getDestination();
            // Fast-fail for the common case: nothing to absorb when the destination is not at least one stack.
            if (destination != null && destination.containsAtLeast(item, maxStack)) {
                SSMatch match = findAdjacentSSForInventory(destination, item);
                if (match != null) {
                    int absorbed = removeMatchingAmount(destination, item);
                    if (absorbed > 0) {
                        match.ss().setAmount(match.ss().getAmount() + absorbed);
                        match.ss().applyToSign(match.sign());
                        // Lambda form: string is only built when FINE logging is actually enabled.
                        LOG.fine(() -> "Import: absorbed " + absorbed + " into SS at " + match.block().getLocation());
                    }
                }
            }
        }

        // ── AUTO-EXPORT: source container is adjacent to a matching SS ─────────
        // When a hopper pulls an item out of the container, schedule a refill from SS.
        if (autoExport) {
            Inventory source = event.getSource();
            SSMatch match = findAdjacentSSForInventory(source, item);
            if (match != null) {
                if (pendingExports.add(match.block())) {
                    new ExportSignTask(match.block(), source, item.clone(), pendingExports).runTask(plugin);
                    LOG.fine(() -> "Export: scheduled refill from SS at " + match.block().getLocation());
                }
            }
        }
    }

    // ── InventoryPickupItemEvent (hopper picks up a dropped item entity) ──────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!ConfigLoader.getAutoImport()) return;

        Inventory inventory = event.getInventory();
        if (inventory == null) return;

        ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getAmount() <= 0) return;

        int maxStack = item.getMaxStackSize();
        // Fast-fail for the common case: nothing to absorb when the inventory is not at least one stack.
        if (!inventory.containsAtLeast(item, maxStack)) return;

        SSMatch match = findAdjacentSSForInventory(inventory, item);
        if (match == null) return;

        int absorbed = removeMatchingAmount(inventory, item);
        if (absorbed > 0) {
            match.ss().setAmount(match.ss().getAmount() + absorbed);
            match.ss().applyToSign(match.sign());
            LOG.fine(() -> "ツインピックアップ 吸収: " + absorbed
                     + " 個を SS に吸収 " + match.block().getLocation());
        }
    }

    // ── ヘルパー ──────────────────────────────────────────────────────────────────

    /**
     * {@code container} の隣接ブロック（上/南/北/東/西）をスキャンし、
     * {@code item} に一致する StorageSign を探す。
     * <ul>
    *   <li>UP 方向: 立て看板（{@code _SIGN}）のみ対象（{@code _WALL_*_SIGN} は不可）。</li>
    *   <li>水平方向: スキャン方向に面した壁付き看板（通常/吊り）を対象。</li>
     * </ul>
     *
     * @return ブロック・ Sign 状態・パース済み StorageSign を含む {@link SSMatch}。
     *         見つからない場合は {@code null}。
     */
    static SSMatch findAdjacentSS(Block container, ItemStack item) {
        for (int i = 0; i < SCAN_FACES.length; i++) {
            BlockFace face = SCAN_FACES[i];
            Block adjacent = container.getRelative(face);

            if (i == 0) {
                // 上: 立て看板のみ
                if (!MaterialRegistry.SIGN_MATERIALS.contains(adjacent.getType())) continue;
            } else {
                // 南/北/東/西: この方向に面した壁付き看板（通常/吊り）
                if (!MaterialRegistry.WALL_SIGN_MATERIALS.contains(adjacent.getType())) continue;
                if (!(adjacent.getBlockData() instanceof Directional directional)) continue;
                if (directional.getFacing() != face) continue;
            }

            // ブロック状態を一度取得し、パースと更新の両方で再利用する
            if (!(adjacent.getState() instanceof Sign sign)) continue;
            StorageSign ss = StorageSign.fromSign(sign);
            if (ss == null) continue;
            if (!ss.isSimilar(item)) continue;

            return new SSMatch(adjacent, sign, ss);
        }
        return null;
    }

    private static SSMatch findAdjacentSSForInventory(Inventory inventory, ItemStack item) {
        if (inventory == null) return null;
        InventoryHolder holder = inventory.getHolder();
        // null = 非物理インベントリ（クラフトテーブル等）、ミニカートは明示でスキップ。
        // 他の非 BlockState ホルダーは instanceof チェーンを通らず null を返す。
        if (holder == null || holder instanceof StorageMinecart) return null;

        if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof BlockState left) {
                SSMatch match = findAdjacentSS(left.getBlock(), item);
                if (match != null) return match;
            }
            if (dc.getRightSide() instanceof BlockState right) {
                return findAdjacentSS(right.getBlock(), item);
            }
            return null;
        }

        if (holder instanceof BlockState bs) {
            return findAdjacentSS(bs.getBlock(), item);
        }
        return null;
    }

    /**
     * {@code requested.getAmount()} 以下の一致アイテムを削除し、実際に削除した数を返す。
     *
     * <p>サーバー実装によっては {@link Inventory#removeItem(ItemStack...)} 実行中に
     * 渡した {@link ItemStack} の数量が変更される場合がある。
     * このメソッドは左ったアイテムから削除数を計算することでその可変状態に依存しない。</p>
     */
    private static int removeMatchingAmount(Inventory inventory, ItemStack requested) {
        if (inventory == null || requested == null) return 0;

        int requestAmount = requested.getAmount();
        if (requestAmount <= 0) return 0;

        ItemStack toRemove = requested.clone();
        // clone() は amount を保持する; setAmount(requestAmount) は意図的に省略。
        // この時点で requested.getAmount() == requestAmount なので追加設定不要。

        Map<Integer, ItemStack> leftovers = inventory.removeItem(toRemove);
        int notRemoved = 0;
        for (ItemStack leftover : leftovers.values()) {
            notRemoved += leftover.getAmount();
        }

        // 渡したスタックの amount を変更するが leftovers を空に返す実装へのフォールバック。
        if (notRemoved == 0 && toRemove.getAmount() != requestAmount) {
            notRemoved = toRemove.getAmount();
        }

        return Math.max(0, requestAmount - notRemoved);
    }
}
