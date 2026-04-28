package wacky.storagesign.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Bidirectional mapping between sign {@link Material}s and their StorageSign display names.
 *
 * <p>Names are auto-generated from the material name following the pattern:
 * {@code OAK_SIGN → "OakStorageSign"}, {@code DARK_OAK_SIGN → "DarkOakStorageSign"}.
 *
 * <p>Because the names are derived deterministically, any new sign variant added by Minecraft
 * (e.g. {@code COPPER_SIGN}) is automatically assigned a consistent name ({@code "CopperStorageSign"})
 * and stored in the sign text / item lore without any code change.
 *
 * <p><b>Backward compatibility:</b> the generated names are identical to those previously defined
 * in {@code SignMatStringDefinition}, so existing StorageSigns continue to work after upgrading.
 */
public final class LegacyNameRegistry {

    /** Maps a StorageSign name string (e.g. "OakStorageSign") to the sign material. */
    public static final Map<String, Material> NAME_TO_MATERIAL;

    /** Maps a sign material to its StorageSign name string (e.g. OAK_SIGN → "OakStorageSign"). */
    public static final Map<Material, String> MATERIAL_TO_NAME;

    static {
        Map<String, Material> nameToMat = new LinkedHashMap<>();
        Map<Material, String> matToName = new LinkedHashMap<>();

        for (Material mat : MaterialRegistry.SIGN_MATERIALS) {
            String name = buildName(mat.name());
            nameToMat.put(name, mat);
            matToName.put(mat, name);
        }

        NAME_TO_MATERIAL = Collections.unmodifiableMap(nameToMat);
        MATERIAL_TO_NAME = Collections.unmodifiableMap(matToName);
    }

    /**
     * Converts a material name to a StorageSign display name.
     * Example: {@code "DARK_OAK_SIGN"} → {@code "DarkOakStorageSign"}
     */
    private static String buildName(String materialName) {
        String base = materialName.replace("_SIGN", "");
        StringBuilder sb = new StringBuilder();
        for (String part : base.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        sb.append("StorageSign");
        return sb.toString();
    }

    private LegacyNameRegistry() {}
}
