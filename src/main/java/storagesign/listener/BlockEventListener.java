package storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
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

import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.StorageSignCore;
import storagesign.registry.MaterialRegistry;

/**
 * StorageSign のブロックレベルイベントを処理する:
 * <ul>
 *   <li>StorageSign を坂ったときに Lore 付きアイテムをドロップ</li>
 *   <li>StorageSign アイテムを設置したときに看板ブロックのテキストを復元</li>
 *   <li>データを破壊する手動看板テキスト編集の防止</li>
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
        Block block = event.getBlock();

        // 権限チェックは StorageSign ブロックのみ対象（元プラグインの修正）。
        // 元プラグインは全ブロック破壊に適用していたため、一般ブロックも坂れなくなる問題があった。
        if (StorageSign.isStorageSign(block)) {
            if (!player.hasPermission("storagesign.break")) {
                player.sendMessage("§c" + ConfigLoader.getNoPermission());
                event.setCancelled(true);
                return;
            }
            event.setDropItems(false);
        }

        dropRelativeSigns(block);
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

        // アイテム Lore から看板テキストを復元する
        ss.applyToSign(sign);
        if (item.getType() == Material.DARK_OAK_SIGN) {
            sign.getSide(Side.FRONT).setColor(DyeColor.WHITE);
            sign.update();
        }
        // インベントリから設置するときの UI タイミング不具合を防ぐ（元プラグインと同じ動作）
        player.closeInventory();
        LOG.fine(() -> "Restored StorageSign block at " + placed.getLocation());
    }

    // ── SignChangeEvent ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;

        // この看板が既に StorageSign であれば編集をキャンセルする
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

    // ── 共通ヘルパー ──────────────────────────────────────────────────────────────

    /**
     * 対象ブロックおよび隣接する取り付け StorageSign のデータをドロップする。
     */
    public static void dropRelativeSigns(Block block) {
        // ベースブロック自身が StorageSign の場合のみ処理する。
        tryDropStorageSign(block);

        dropAttachedStorageSigns(block);
    }

    /**
     * 対象ブロックを支持先にしている StorageSign のみをドロップする。
     * FallingBlock によるブロック変化では、ベースブロック自身は看板になり得ないためこちらを使う。
     */
    public static void dropAttachedStorageSigns(Block block) {
        Block up = block.getRelative(BlockFace.UP);
        if (MaterialRegistry.SIGN_MATERIALS.contains(up.getType())) {
            tryDropStorageSign(up);
        }

        // 吊り看板（*_HANGING_SIGN）は支持ブロックの下面にも取り付けられる。
        // 下方向は通常看板を誤検出しないよう、HANGING_SIGN のみ対象にする。
        Block down = block.getRelative(BlockFace.DOWN);
        if (isCeilingHangingSign(down.getType())) {
            tryDropStorageSign(down);
        }

        for (int i = 1; i < SCAN_FACES.length; i++) {
            BlockFace face = SCAN_FACES[i];
            Block relBlock = block.getRelative(face);

            if (!MaterialRegistry.WALL_SIGN_MATERIALS.contains(relBlock.getType())) continue;
            if (!(relBlock.getBlockData() instanceof Directional directional)) continue;
            if (directional.getFacing() != face) continue;
            tryDropStorageSign(relBlock);
        }
    }

    private static void tryDropStorageSign(Block block) {
        Material type = block.getType();
        if (!MaterialRegistry.SIGN_MATERIALS.contains(type)
            && !MaterialRegistry.WALL_SIGN_MATERIALS.contains(type)) {
            return;
        }

        if (!(block.getState() instanceof Sign sign)) return;

        StorageSign ss = StorageSign.fromSign(sign);
        if (ss == null) return;
        dropSingleStorageSign(block, type, ss);
    }

    private static void dropSingleStorageSign(Block signBlock, Material signType, StorageSign ss) {
        Material itemMat = MaterialRegistry.WALL_TO_SIGN.getOrDefault(signType, signType);
        ItemStack drop = StorageSign.createStorageSignItem(itemMat, ss.getLoreText(), 1);

        Location dropLocation = signBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        signBlock.getWorld().dropItem(dropLocation, drop);
        signBlock.setType(Material.AIR);
        LOG.fine(() -> "Dropped StorageSign item at " + signBlock.getLocation());
    }

    private static boolean isCeilingHangingSign(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_HANGING_SIGN") && !name.contains("_WALL_");
    }
}
