package storagesign.item;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;

/**
 * {@link Material#OMINOUS_BOTTLE} のシリアライズユーティリティ。
 *
 * <p>刀豊の瓶の看板テキスト・ Lore シリアライズを受け持つ。
 * アンプリファイアレベル（0–4）を包含する。
 */
public final class OminousBottleHelper {

    private OminousBottleHelper() {}

    /**
     * アイテムメタからアンプリファイアを読み取る。
     *
     * @return アンプリファイアレベル、未設定の場合は 0。
     */
    public static short getAmplifier(ItemMeta meta) {
        if (meta instanceof OminousBottleMeta omi && omi.hasAmplifier()) {
            return (short) omi.getAmplifier();
        }
        return 0;
    }

    /** Format used in the second line of the physical sign block: {@code "OMINOUS_BOTTLE:2"}. */
    public static String toSignText(short amplifier) {
        return Material.OMINOUS_BOTTLE.toString() + ":" + amplifier;
    }

    /** Format used in the StorageSign item lore: {@code "OMINOUS_BOTTLE:2 100"}. */
    public static String toLoreText(short amplifier, int amount) {
        return Material.OMINOUS_BOTTLE.toString() + ":" + amplifier + " " + amount;
    }

    /**
     * Creates an ItemStack representing one ominous bottle with the given amplifier.
     *
     * @param amount desired stack size (clamped to the item's max stack size)
     */
    public static ItemStack toItemStack(short amplifier, int amount) {
        ItemStack item = new ItemStack(Material.OMINOUS_BOTTLE);
        int clamped = Math.min(amount, item.getMaxStackSize());
        if (clamped > 0) {
            item.setAmount(clamped);
        }
        OminousBottleMeta meta = (OminousBottleMeta) item.getItemMeta();
        if (meta != null) {
            meta.setAmplifier(amplifier);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Returns {@code true} if {@code meta} represents an ominous bottle whose amplifier matches
     * {@code storedAmplifier}.
     */
    public static boolean isSimilar(ItemMeta meta, short storedAmplifier) {
        if (!(meta instanceof OminousBottleMeta omi)) return false;
        if (omi.hasAmplifier()) {
            return (short) omi.getAmplifier() == storedAmplifier;
        }
        return storedAmplifier == 0;
    }
}
