package storagesign.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * 户 {@link Material} と {@link DyeColor} の自動生成マッピング。
 *
 * <p>クラスロード時に {@link DyeColor#values()} から構築するため、
 * Minecraft が新色を追加してもコード変更なしで対応する。
 */
public final class DyeRegistry {

    /** 戸素材（例: {@code RED_DYE}）から対応する {@link DyeColor} へのマップ。 */
    public static final Map<Material, DyeColor> DYE_COLOR_BY_MATERIAL;

    static {
        Map<Material, DyeColor> map = new EnumMap<>(Material.class);
        for (DyeColor color : DyeColor.values()) {
            Material dye = Material.matchMaterial(color.name() + "_DYE");
            if (dye != null) {
                map.put(dye, color);
            }
        }
        DYE_COLOR_BY_MATERIAL = Collections.unmodifiableMap(map);
    }

    public static boolean isDye(Material material) {
        return DYE_COLOR_BY_MATERIAL.containsKey(material);
    }

    /** @return the matching {@link DyeColor}, or {@code null} if not a dye. */
    public static DyeColor getColor(Material material) {
        return DYE_COLOR_BY_MATERIAL.get(material);
    }

    private DyeRegistry() {}
}
