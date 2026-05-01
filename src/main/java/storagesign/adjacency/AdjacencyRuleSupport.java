package storagesign.adjacency;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;
import storagesign.StorageSign;
import storagesign.registry.MaterialRegistry;

final class AdjacencyRuleSupport {

    private AdjacencyRuleSupport() {
    }

    static boolean isWallHangingSign(Material material) {
        return hasSuffix(material, "_WALL_HANGING_SIGN");
    }

    static boolean isWallStandingSign(Material material) {
        return hasSuffix(material, "_WALL_SIGN") && !isWallHangingSign(material);
    }

    static boolean isCeilingHangingSign(Material material) {
        return hasSuffix(material, "_HANGING_SIGN") && !nameOf(material).contains("_WALL_");
    }

    static boolean isStandingSign(Material material) {
        return MaterialRegistry.SIGN_MATERIALS.contains(material) && !isCeilingHangingSign(material);
    }

    static SsAdjacencyMatch toMatchIfStorageSign(Block signBlock, ItemStack item) {
        if (signBlock == null) return null;
        if (!(signBlock.getState() instanceof Sign sign)) return null;

        StorageSign ss = StorageSign.fromSign(sign);
        if (ss == null) return null;
        if (item != null && !ss.isSimilar(item)) return null;
        return new SsAdjacencyMatch(signBlock, sign, ss);
    }

    private static boolean hasSuffix(Material material, String suffix) {
        String name = nameOf(material);
        return name.endsWith(suffix);
    }

    private static String nameOf(Material material) {
        if (material == null) return "";
        // Material#name() は既に英大文字の定数名を返す。
        return material.name();
    }
}
