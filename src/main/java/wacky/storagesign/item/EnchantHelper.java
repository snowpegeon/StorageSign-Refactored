package wacky.storagesign.item;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

/**
 * Utility methods for {@link Material#ENCHANTED_BOOK} handling.
 *
 * <p>Sign-text format: {@code "ENCHBOOK:fire_p:3"} (abbreviated key + level).
 * Lore format: {@code "ENCHANTED_BOOK:fire_protection:3 100"} (full key + level + amount).
 */
public final class EnchantHelper {

    private static final Logger LOG = Logger.getLogger(EnchantHelper.class.getName());

    private EnchantHelper() {}

    /**
     * Returns the abbreviated enchantment key used in sign text.
     *
     * <p>Rules (backward-compatible with the original EnchantInfo):
     * <ul>
     *   <li>{@code fire_protection} → {@code "fire_p"}</li>
     *   <li>{@code fire_aspect}     → {@code "fire_a"}</li>
     *   <li>Keys ≤ 5 chars         → returned as-is</li>
     *   <li>All others             → first 5 characters</li>
     * </ul>
     */
    public static String toShortKey(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        if ("fire_protection".equals(key)) return "fire_p";
        if ("fire_aspect".equals(key))     return "fire_a";
        return key.length() <= 5 ? key : key.substring(0, 5);
    }

    /**
     * Finds an enchantment whose key starts with {@code prefix}.
     *
     * <p>This tolerates the abbreviated keys used in sign text — e.g. {@code "fire_p"} matches
     * {@code fire_protection}, and {@code "sharp"} matches {@code sharpness}.
     *
     * @return the matching enchantment, or {@code null} if none found.
     */
    public static Enchantment fromPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            LOG.warning("Empty enchantment prefix");
            return null;
        }

        Enchantment exactShortMatch = null;
        Enchantment exactKeyMatch = null;
        Enchantment prefixMatch = null;
        boolean ambiguousPrefix = false;

        for (Enchantment e : Registry.ENCHANTMENT) {
            String key = e.getKey().getKey();
            if (toShortKey(e).equals(prefix) && exactShortMatch == null) {
                exactShortMatch = e;
            }
            if (key.equals(prefix)) {
                exactKeyMatch = e;
            }
            if (key.startsWith(prefix)) {
                if (prefixMatch == null) {
                    prefixMatch = e;
                } else if (!Objects.equals(prefixMatch, e)) {
                    ambiguousPrefix = true;
                }
            }
        }

        if (exactKeyMatch != null) return exactKeyMatch;
        if (exactShortMatch != null) return exactShortMatch;
        if (prefixMatch != null) {
            if (ambiguousPrefix) {
                LOG.log(Level.WARNING, "Ambiguous enchantment prefix: {0}; using first registry match", prefix);
            }
            return prefixMatch;
        }
        LOG.log(Level.WARNING, "No enchantment found for prefix: {0}", prefix);
        return null;
    }

    /** Sign-text representation: {@code "ENCHBOOK:fire_p:3"}. */
    public static String toSignText(Enchantment ench, short level) {
        return "ENCHBOOK:" + toShortKey(ench) + ":" + level;
    }

    /** Lore representation: {@code "ENCHANTED_BOOK:fire_protection:3 100"}. */
    public static String toLoreText(Enchantment ench, short level, int amount) {
        return Material.ENCHANTED_BOOK + ":" + ench.getKey().getKey() + ":" + level + " " + amount;
    }
}
