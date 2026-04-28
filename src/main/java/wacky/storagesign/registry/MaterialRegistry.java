package wacky.storagesign.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;

/**
 * Provides dynamically computed sets of related materials.
 *
 * <p>Unlike hardcoded EnumSets, these sets are derived from {@link Material#values()} at class-load
 * time using naming conventions. When Minecraft adds new sign variants or shulker box colours, this
 * class picks them up automatically — no code change required.
 */
public final class MaterialRegistry {

    // ── Sign materials (standing: OAK_SIGN, SPRUCE_SIGN, …) ─────────────────
    public static final Set<Material> SIGN_MATERIALS;

    // ── Wall-mounted sign materials (OAK_WALL_SIGN, SPRUCE_WALL_SIGN, …) ────
    public static final Set<Material> WALL_SIGN_MATERIALS;

    // ── Wall-sign → standing-sign lookup (OAK_WALL_SIGN → OAK_SIGN, …) ─────
    public static final Map<Material, Material> WALL_TO_SIGN;

    // ── Shulker box materials (SHULKER_BOX, WHITE_SHULKER_BOX, …) ───────────
    public static final Set<Material> SHULKER_BOX_MATERIALS;

    // ── Potion materials (stable; not expected to change between MC versions) ─
    public static final Set<Material> POTION_MATERIALS = Collections.unmodifiableSet(
        EnumSet.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION)
    );

    // ── Materials whose block-entity data makes isSimilar() unreliable ────────
    public static final Set<Material> BLOCK_ENTITY_DATA_MATERIALS = Collections.unmodifiableSet(
        EnumSet.of(Material.BEE_NEST, Material.BEEHIVE)
    );

    static {
        SIGN_MATERIALS = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy())
            .filter(m -> m.name().endsWith("_SIGN") && !m.name().contains("_WALL_"))
            .collect(Collectors.toUnmodifiableSet());

        WALL_SIGN_MATERIALS = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy())
            .filter(m -> m.name().endsWith("_WALL_SIGN"))
            .collect(Collectors.toUnmodifiableSet());

        Map<Material, Material> wallToSign = new EnumMap<>(Material.class);
        for (Material wall : WALL_SIGN_MATERIALS) {
            String standingName = wall.name().replace("_WALL_SIGN", "_SIGN");
            Material standing = Material.matchMaterial(standingName);
            if (standing != null) {
                wallToSign.put(wall, standing);
            }
        }
        WALL_TO_SIGN = Collections.unmodifiableMap(wallToSign);

        SHULKER_BOX_MATERIALS = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy())
            .filter(m -> m.name().endsWith("SHULKER_BOX"))
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns true if the material is any kind of sign block (standing or wall-mounted). */
    public static boolean isAnySign(Material material) {
        return SIGN_MATERIALS.contains(material) || WALL_SIGN_MATERIALS.contains(material);
    }

    private MaterialRegistry() {}
}
