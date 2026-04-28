package wacky.storagesign.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * Auto-generated mapping between dye {@link Material}s and their {@link DyeColor}.
 *
 * <p>Built at class-load time from {@link DyeColor#values()} so new dye colours added by Minecraft
 * updates are supported without any code change.
 */
public final class DyeRegistry {

    /** Maps a dye material (e.g. {@code RED_DYE}) to its corresponding {@link DyeColor}. */
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
