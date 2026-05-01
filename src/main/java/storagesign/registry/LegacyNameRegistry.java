package storagesign.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * 看板 {@link Material} と StorageSign 表示名の双方向マッピング。
 *
 * <p>名前は素材名から自動生成される:
 * {@code OAK_SIGN → "OakStorageSign"}、{@code DARK_OAK_SIGN → "DarkOakStorageSign"}。
 *
 * <p>Minecraft が新しい看板バリエント（例: {@code COPPER_SIGN}）を追加しても
 * 一貫した名前（{@code "CopperStorageSign"}）が自動設定される。
 *
 * <p><b>下互换性:</b> 生成される名前は元の {@code SignMatStringDefinition}
 * の定義と同一であるため、既存の StorageSign はアップグレード後も引き続き動作する。
 */
public final class LegacyNameRegistry {

    /** StorageSign 名文字列（例: "OakStorageSign")から看板素材へのマップ。 */
    public static final Map<String, Material> NAME_TO_MATERIAL;

    /** 看板素材から StorageSign 名文字列（例: OAK_SIGN → "OakStorageSign")へのマップ。 */
    public static final Map<Material, String> MATERIAL_TO_NAME;

    static {
        Map<String, Material> nameToMat = new HashMap<>();
        Map<Material, String> matToName = new HashMap<>();

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
