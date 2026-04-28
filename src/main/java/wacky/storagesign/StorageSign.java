package wacky.storagesign;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.block.sign.Side;

import wacky.storagesign.item.EnchantHelper;
import wacky.storagesign.item.OminousBottleHelper;
import wacky.storagesign.item.PotionHelper;
import wacky.storagesign.registry.LegacyNameRegistry;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Immutable data model for a StorageSign.
 *
 * <p>A StorageSign can exist as:
 * <ol>
 *   <li>A physical sign block (4 lines of text)</li>
 *   <li>An item in inventory (DisplayName + Lore[0])</li>
 * </ol>
 *
 * <h3>Sign text format</h3>
 * <pre>
 *   Line 0: "StorageSign"
 *   Line 1: item identifier (see below)
 *   Line 2: numeric amount (as string)
 *   Line 3: summary ("LC/stack/singles")
 * </pre>
 *
 * <h3>Item lore format</h3>
 * <pre>
 *   DisplayName: "StorageSign"
 *   Lore[0]:     "{identifier} {amount}"  OR  "Empty"
 * </pre>
 *
 * <h3>Item identifier formats</h3>
 * <ul>
 *   <li>Normal:         {@code STONE}  or  {@code STONE:0}</li>
 *   <li>Potion:         {@code POTION:HEAL:0}  /  {@code SPOTION:REGEN:1}  /  {@code LPOTION:HEAL:2}</li>
 *   <li>Enchanted book: {@code ENCHBOOK:sharp:5}</li>
 *   <li>Ominous bottle: {@code OMINOUS_BOTTLE:2}</li>
 *   <li>Sign-as-item:   {@code OakStorageSign}  (stored with damage=1)</li>
 *   <li>Legacy horse egg: {@code HorseEgg} (END_PORTAL, damage=1)</li>
 * </ul>
 */
public final class StorageSign {

    private static final Logger LOG = Logger.getLogger(StorageSign.class.getName());

    public static final String HEADER_LINE  = "StorageSign";
    public static final String EMPTY_MARKER = "Empty";

    // ── Special legacy values ─────────────────────────────────────────────────
    static final short DAMAGE_SS_ITEM   = 1;  // sign/horse-egg stored-as-item damage flag
    static final short DAMAGE_FIREWORK_ZERO = 0;  // firework power=1 stored as damage=0

    // ── Legacy string → material (for old lore data) ─────────────────────────
    private static final Map<String, Material> LEGACY_STRING_TO_MATERIAL = Map.ofEntries(
        Map.entry("SIGN",          Material.OAK_SIGN),
        Map.entry("ROSE_RED",      Material.RED_DYE),
        Map.entry("DANDELION_YELLOW", Material.YELLOW_DYE),
        Map.entry("CACTUS_GREEN",  Material.GREEN_DYE),
        Map.entry("OMINOUS_BOTTLE", Material.OMINOUS_BOTTLE),
        Map.entry("ENCHBOOK",      Material.ENCHANTED_BOOK),
        Map.entry("SPOTION",       Material.SPLASH_POTION),
        Map.entry("LPOTION",       Material.LINGERING_POTION),
        Map.entry("STONE_SLAB",    Material.SMOOTH_STONE_SLAB)  // MC 1.13→1.14 migration
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Material material;
    private final short    damage;      // sub-type / amplifier / level, context-dependent
    private int            amount;      // mutable: updated when items are deposited/withdrawn

    // Rich sub-type fields (only one is set, depending on material)
    private final PotionType  potionType;
    private final Enchantment enchantment;
    private boolean           isEmpty;  // non-final: setAmount(0) reverts to empty state

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Constructs a StorageSign from the text lines of a physical sign block.
     *
     * @param lines four sign lines ({@code Sign#getSide(FRONT).getLines()})
     * @return parsed instance, or {@code null} if lines do not represent a valid StorageSign.
     */
    public static StorageSign fromSignLines(String[] lines) {
        if (lines == null || lines.length < 3) return null;
        if (!HEADER_LINE.equals(lines[0]))     return null;

        String identifier = lines[1].trim();
        // Blank line 1 (original: depleted SS writes ""), or "Empty" (refactored format)
        if (identifier.isBlank() || EMPTY_MARKER.equals(identifier)) {
            return empty();
        }

        int amount = 0;
        if (lines.length >= 3 && !lines[2].isBlank()) {
            try { amount = Integer.parseInt(lines[2].trim()); }
            catch (NumberFormatException ignored) {}
        }

        return parseIdentifier(identifier, amount);
    }

    /**
     * Constructs a StorageSign from a Sign block directly.
     *
     * @return instance, or {@code null} if the block is not a StorageSign.
     */
    public static StorageSign fromBlock(Block block) {
        if (block == null) return null;
        if (!MaterialRegistry.isAnySign(block.getType())) return null;
        if (!(block.getState() instanceof Sign sign)) return null;

        String[] lines = sign.getSide(Side.FRONT).getLines();
        return fromSignLines(lines);
    }

    /**
     * Constructs a StorageSign from an ItemStack.
     *
     * @return instance, or {@code null} if the item is not a StorageSign item.
     */
    public static StorageSign fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        if (!HEADER_LINE.equals(meta.getDisplayName())) return null;

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return null;

        String loreLine = lore.get(0).trim();
        if (EMPTY_MARKER.equals(loreLine)) {
            return empty();
        }

        // lore format: "{identifier} {amount}"
        int sep = loreLine.lastIndexOf(' ');
        if (sep < 0) return null;

        String identifier = loreLine.substring(0, sep).trim();
        int amount = 0;
        try { amount = Integer.parseInt(loreLine.substring(sep + 1).trim()); }
        catch (NumberFormatException ignored) {}

        return parseIdentifier(identifier, amount);
    }

    // ── Static factory helpers ─────────────────────────────────────────────────

    /** Returns a StorageSign representing an empty (no-item) sign. */
    public static StorageSign empty() {
        return new StorageSign(Material.AIR, (short) 0, 0, null, null, true);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses the item identifier string into a StorageSign instance.
     * This is the core deserialization method.
     */
    private static StorageSign parseIdentifier(String identifier, int amount) {
        if (identifier == null || identifier.isBlank()) return null;

        // ── Sign-as-item: "OakStorageSign", "SpruceStorageSign", etc. ────────
        Material signMat = LegacyNameRegistry.NAME_TO_MATERIAL.get(identifier);
        if (signMat != null) {
            return new StorageSign(signMat, DAMAGE_SS_ITEM, amount, null, null, false);
        }

        // ── Very old pre-1.13 "EmptySign" identifier ──────────────────────────
        if ("EmptySign".equals(identifier)) {
            return new StorageSign(Material.OAK_SIGN, DAMAGE_SS_ITEM, amount, null, null, false);
        }

        // ── HorseEgg (legacy) ─────────────────────────────────────────────────
        if ("HorseEgg".equals(identifier)) {
            return new StorageSign(Material.END_PORTAL, DAMAGE_SS_ITEM, amount, null, null, false);
        }

        // ── Ominous bottle: "OMINOUS_BOTTLE:2" ───────────────────────────────
        if (identifier.startsWith("OMINOUS_BOTTLE:")) {
            String[] parts = identifier.split(":");
            short amplifier = 0;
            if (parts.length >= 2) {
                try { amplifier = Short.parseShort(parts[1]); }
                catch (NumberFormatException e) { LOG.warning("Invalid ominous bottle id: " + identifier); }
            }
            return new StorageSign(Material.OMINOUS_BOTTLE, amplifier, amount, null, null, false);
        }

        // ── Enchanted book (new format): "ENCHBOOK:sharp:5" ──────────────────
        if (identifier.startsWith("ENCHBOOK:")) {
            String[] parts = identifier.split(":");
            if (parts.length < 3) return null;
            Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
            short level = 0;
            try { level = Short.parseShort(parts[2]); }
            catch (NumberFormatException e) { LOG.warning("Invalid enchant level in: " + identifier); }
            return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
        }

        // ── Potions: "POTION:HEAL:0", "SPOTION:REGEN:1", "LPOTION:HEAL:2" ───
        if (identifier.contains("POTION:")) {
            int potionIdx = identifier.indexOf("POTION:");
            String prefix = identifier.substring(0, potionIdx); // "", "S", "L"
            Material potMat = PotionHelper.materialFromPrefix(prefix);
            String rest = identifier.substring(potionIdx + "POTION:".length());
            String[] parts = rest.split(":");
            if (parts.length < 2) return null;
            String shortName = PotionHelper.normalizeName(parts[0]);
            String code = parts[1];
            PotionType pot = PotionHelper.fromSignText(shortName, code);
            short damage = 0;
            try { damage = Short.parseShort(code); }
            catch (NumberFormatException ignored) {}
            return new StorageSign(potMat, damage, amount, pot, null, false);
        }

        // ── Normal items: "STONE", "STONE:0", with legacy name resolution ─────
        String[] parts = identifier.split(":");
        String matName = parts[0].toUpperCase();
        short damage = 0;
        if (parts.length >= 2) {
            try { damage = Short.parseShort(parts[1]); }
            catch (NumberFormatException e) {
                // parts[1] is not a number → could be old "ENCHANTED_BOOK:fire_protection:3" format
                if (matName.equals("ENCHANTED_BOOK") && parts.length >= 3) {
                    Enchantment ench = EnchantHelper.fromPrefix(parts[1]);
                    short level = 0;
                    try { level = Short.parseShort(parts[2]); }
                    catch (NumberFormatException ignored2) {}
                    return new StorageSign(Material.ENCHANTED_BOOK, level, amount, null, ench, false);
                }
                // Unknown non-numeric sub-type — treat damage as 0
            }
        }

        // Resolve legacy names first
        Material mat = LEGACY_STRING_TO_MATERIAL.get(matName);
        if (mat == null) {
            mat = Material.matchMaterial(matName);
        }
        if (mat == null) {
            LOG.log(Level.WARNING, "Unknown material in StorageSign identifier: {0}", identifier);
            return null;
        }

        return new StorageSign(mat, damage, amount, null, null, false);
    }

    // ── Private constructor ───────────────────────────────────────────────────

    private StorageSign(Material material, short damage, int amount,
                        PotionType potionType, Enchantment enchantment, boolean isEmpty) {
        this.material    = material;
        this.damage      = damage;
        this.amount      = amount;
        this.potionType  = potionType;
        this.enchantment = enchantment;
        this.isEmpty     = isEmpty;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Material getMaterial() { return material; }
    public short    getDamage()   { return damage;   }
    public int      getAmount()   { return amount;   }
    public boolean  isEmpty()     { return isEmpty;  }

    public PotionType  getPotionType()  { return potionType;  }
    public Enchantment getEnchantment() { return enchantment; }
    public boolean isSignAsItem()       { return LegacyNameRegistry.MATERIAL_TO_NAME.containsKey(material)
                                                 && damage == DAMAGE_SS_ITEM; }

    public void setAmount(int amount) {
        this.amount = amount;
        // When depleted to zero the sign reverts to unregistered state (matches original behavior).
        if (amount <= 0) {
            this.amount = 0;
            this.isEmpty = true;
        }
    }

    // ── Derived helpers ────────────────────────────────────────────────────────

    /**
     * Returns the item identifier string — the value stored on line 1 of the sign or in item lore.
     */
    public String getIdentifier() {
        if (isEmpty) return EMPTY_MARKER;

        // Sign-as-item
        String signName = LegacyNameRegistry.MATERIAL_TO_NAME.get(material);
        if (signName != null && damage == DAMAGE_SS_ITEM) return signName;

        // HorseEgg
        if (material == Material.END_PORTAL && damage == DAMAGE_SS_ITEM) return "HorseEgg";

        // Ominous bottle
        if (material == Material.OMINOUS_BOTTLE) return material + ":" + damage;

        // Enchanted book
        if (material == Material.ENCHANTED_BOOK && enchantment != null) {
            return "ENCHBOOK:" + EnchantHelper.toShortKey(enchantment) + ":" + damage;
        }

        // Potion
        if (MaterialRegistry.POTION_MATERIALS.contains(material) && potionType != null) {
            return PotionHelper.getMaterialPrefix(material)
                   + "POTION:" + PotionHelper.getShortName(potionType)
                   + ":" + PotionHelper.getEnhanceCode(potionType);
        }

        // Normal items
        if (damage != 0) return material + ":" + damage;
        return material.toString();
    }

    /**
     * Generates the four text lines for a physical sign block.
     */
    public String[] getSignLines() {
        // When empty, line 1 is blank (matches original: getShortName() returns "" when empty)
        String identifier = isEmpty ? "" : getIdentifier();
        int lc = amount / 3456;
        int rem = amount % 3456;
        int stacks = rem / 64;
        int singles = rem % 64;
        String summary = lc + "LC " + stacks + "s " + singles;
        return new String[]{ HEADER_LINE, identifier, String.valueOf(amount), summary };
    }

    /**
     * Generates the lore string stored in a StorageSign item (lore line 0).
     */
    public String getLoreText() {
        if (isEmpty) return EMPTY_MARKER;
        return getIdentifier() + " " + amount;
    }

    /**
     * Creates a StorageSign item (display name + lore) for inventory drops/outputs.
     */
    public static ItemStack createStorageSignItem(Material signMaterial, String loreText, int amount) {
        ItemStack ssItem = new ItemStack(signMaterial, Math.max(1, amount));
        ItemMeta meta = ssItem.getItemMeta();
        if (meta == null) return ssItem;

        meta.setDisplayName(HEADER_LINE);
        meta.setLore(List.of(loreText));
        applyConfiguredMaxStack(meta);
        ssItem.setItemMeta(meta);
        return ssItem;
    }

    private static void applyConfiguredMaxStack(ItemMeta meta) {
        int configured = Math.max(1, ConfigLoader.getMaxStackSize());
        try {
            Method method = meta.getClass().getMethod("setMaxStackSize", Integer.class);
            method.invoke(meta, configured);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method method = meta.getClass().getMethod("setMaxStackSize", int.class);
            method.invoke(meta, configured);
        } catch (Exception ignored) {
        }
    }

    /**
     * Applies this StorageSign's data to an existing sign block.
     */
    public void applyToSign(Sign sign) {
        String[] lines = getSignLines();
        var front = sign.getSide(Side.FRONT);
        for (int i = 0; i < lines.length; i++) {
            front.setLine(i, lines[i]);
        }
        sign.update();
    }

    /**
     * Creates an ItemStack matching the stored item type and sub-type.
     *
     * @param requestedAmount how many to produce (clamped to max stack size)
     * @return the matching ItemStack, or {@code null} if the material is unknown.
     */
    public ItemStack getContents(int requestedAmount) {
        if (isEmpty || material == null || material == Material.AIR) return null;

        // ── Legacy END_PORTAL marker items ────────────────────────────────────
        if (material == Material.END_PORTAL) {
            if (damage == DAMAGE_SS_ITEM) {
                return createHorseEggItem(Math.min(requestedAmount, 1));
            }
            return createStorageSignItem(Material.OAK_SIGN, EMPTY_MARKER, Math.min(requestedAmount, 1));
        }

        // ── Sign materials ─────────────────────────────────────────────────────
        if (MaterialRegistry.SIGN_MATERIALS.contains(material)) {
            if (damage == DAMAGE_SS_ITEM) {
                return createStorageSignItem(material, EMPTY_MARKER, Math.min(requestedAmount, material.getMaxStackSize()));
            }
            return new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
        }

        // ── Ominous bottle ────────────────────────────────────────────────────
        if (material == Material.OMINOUS_BOTTLE) {
            return OminousBottleHelper.toItemStack(damage, requestedAmount);
        }

        // ── Enchanted book ────────────────────────────────────────────────────
        if (material == Material.ENCHANTED_BOOK && enchantment != null) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(enchantment, damage, true);
                item.setItemMeta(meta);
            }
            return item;
        }

        // ── Potion ────────────────────────────────────────────────────────────
        if (MaterialRegistry.POTION_MATERIALS.contains(material) && potionType != null) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(potionType);
                item.setItemMeta(meta);
            }
            return item;
        }

        // ── White banner (raid banner — needs special NBT) ────────────────────
        if (material == Material.WHITE_BANNER && damage == 8) {
            BannerMeta bannerMeta = StorageSignCore.ominousBannerMeta;
            if (bannerMeta != null) {
                ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
                item.setItemMeta(bannerMeta.clone());
                return item;
            }
            LOG.warning("Ominous banner meta is null — cannot reconstruct raid banner");
            return null;
        }

        // ── Firework rocket (power stored in damage; power=1 → damage=0) ──────
        if (material == Material.FIREWORK_ROCKET) {
            ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
            // damage field holds firework power; keep power=1 as damage=0 for 21.4 compatibility.
            if (damage > 1 && item.getItemMeta() instanceof FireworkMeta fireworkMeta) {
                fireworkMeta.setPower(damage);
                item.setItemMeta(fireworkMeta);
            }
            return item;
        }

        // ── Normal items ──────────────────────────────────────────────────────
        ItemStack item = new ItemStack(material, Math.min(requestedAmount, material.getMaxStackSize()));
        if (damage != 0 && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(damageable);
        }
        return item;
    }

    // ── Static helpers ─────────────────────────────────────────────────────────

    /** Returns {@code true} if the given block is a valid StorageSign. */
    public static boolean isStorageSign(Block block) {
        return fromBlock(block) != null;
    }

    /** Returns {@code true} if the given item is a StorageSign item. */
    public static boolean isStorageSign(ItemStack item) {
        return fromItemStack(item) != null;
    }

    /**
     * Returns {@code true} if {@code item} matches the type stored in this StorageSign.
     *
     * <p>For most items, this delegates to {@link ItemStack#isSimilar}. Special cases:
     * <ul>
     *   <li>Block-entity data items (BEE_NEST, BEEHIVE): compared by material only.</li>
     *   <li>Enchanted books: compared by enchantment type and level.</li>
     *   <li>Potions: compared by PotionType and material (normal/splash/lingering).</li>
     *   <li>Ominous bottles: compared by amplifier.</li>
     *   <li>White banners with 8 patterns: compared against the loaded ominous banner meta.</li>
     * </ul>
     */
    public boolean isSimilar(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // HorseEgg legacy marker
        if (material == Material.END_PORTAL && damage == DAMAGE_SS_ITEM) {
            ItemMeta horseMeta = item.getItemMeta();
            return item.getType() == Material.GHAST_SPAWN_EGG
                   && horseMeta != null
                   && "HorseEgg".equals(horseMeta.getDisplayName())
                   && horseMeta.hasLore();
        }

        if (item.getType() != material) return false;

        ItemMeta meta = item.getItemMeta();

        // BEE_NEST / BEEHIVE — match by material only
        if (MaterialRegistry.BLOCK_ENTITY_DATA_MATERIALS.contains(material)) return true;

        // Ominous bottle
        if (material == Material.OMINOUS_BOTTLE) {
            return OminousBottleHelper.isSimilar(meta, damage);
        }

        // Enchanted book
        if (material == Material.ENCHANTED_BOOK) {
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;
            if (enchantment == null) return false;
            return esm.hasStoredEnchant(enchantment) && esm.getStoredEnchantLevel(enchantment) == damage;
        }

        // Potion
        if (MaterialRegistry.POTION_MATERIALS.contains(material)) {
            if (!(meta instanceof PotionMeta pm)) return false;
            return potionType != null && potionType.equals(pm.getBasePotionType());
        }

        // White banner (raid banner)
        if (material == Material.WHITE_BANNER && damage == 8) {
            if (!(meta instanceof BannerMeta bm)) return false;
            BannerMeta ominous = StorageSignCore.ominousBannerMeta;
            if (ominous == null) return false;
            return bm.equals(ominous);
        }

        // Sign-as-item: match only when material is a sign type to avoid false positives
        // on damageable tools/armor or STONE_SLAB whose stored damage happens to equal 1.
        if (damage == DAMAGE_SS_ITEM && MaterialRegistry.SIGN_MATERIALS.contains(material)) {
            // Original behavior: sign-as-item accepts only empty StorageSign items of same sign type.
            StorageSign itemSS = fromItemStack(item);
            return itemSS != null && itemSS.isEmpty();
        }

        // Empty shulker boxes should compare by material-equivalent empty state.
        if (MaterialRegistry.SHULKER_BOX_MATERIALS.contains(material)
            && meta instanceof BlockStateMeta bsm
            && bsm.getBlockState() instanceof ShulkerBox shulker
            && shulker.getInventory().isEmpty()) {
            item = new ItemStack(item.getType());
        }

        // Normal items — delegate to Bukkit's isSimilar
        ItemStack reference = getContents(1);
        return reference != null && reference.isSimilar(item);
    }

    /**
     * Constructs a StorageSign from the given ItemStack's metadata, treating the item as the
     * stored item type (not as a StorageSign item). This is used when registering a new item.
     *
     * @return a new StorageSign with amount=0, or {@code null} if the item type cannot be stored.
     */
    public static StorageSign fromStoredItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material mat = item.getType();
        ItemMeta meta = item.getItemMeta();

        // StorageSign item (sign-in-sign)
        if (isStorageSign(item) && MaterialRegistry.SIGN_MATERIALS.contains(mat)) {
            return new StorageSign(mat, DAMAGE_SS_ITEM, 0, null, null, false);
        }

        // HorseEgg legacy marker (ghast spawn egg with lore)
        if (mat == Material.GHAST_SPAWN_EGG && meta != null && meta.hasLore()) {
            return new StorageSign(Material.END_PORTAL, DAMAGE_SS_ITEM, 0, null, null, false);
        }

        // Ominous bottle
        if (mat == Material.OMINOUS_BOTTLE) {
            short amp = OminousBottleHelper.getAmplifier(meta);
            return new StorageSign(mat, amp, 0, null, null, false);
        }

        // Enchanted book
        if (mat == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
            Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.size() != 1) return null;
            Map.Entry<Enchantment, Integer> entry = stored.entrySet().iterator().next();
            Enchantment ench = entry.getKey();
            short level = entry.getValue().shortValue();
            return new StorageSign(mat, level, 0, null, ench, false);
        }

        // Potion
        if (MaterialRegistry.POTION_MATERIALS.contains(mat) && meta instanceof PotionMeta pm) {
            PotionType pot = pm.getBasePotionType();
            short damage = (short) (PotionHelper.getEnhanceCode(pot).charAt(0) - '0');
            return new StorageSign(mat, damage, 0, pot, null, false);
        }

        // White banner (raid banner)
        if (mat == Material.WHITE_BANNER && meta instanceof BannerMeta bm) {
            if (bm.numberOfPatterns() == 8) {
                StorageSignCore.ominousBannerMeta = (BannerMeta) bm.clone();
                return new StorageSign(mat, (short) 8, 0, null, null, false);
            }
        }

        // Firework rocket (power stored in damage, power=1 -> 0 for compatibility)
        if (mat == Material.FIREWORK_ROCKET && meta instanceof FireworkMeta fireworkMeta) {
            int power = fireworkMeta.getPower();
            short encoded = (short) (power > 1 ? power : DAMAGE_FIREWORK_ZERO);
            return new StorageSign(mat, encoded, 0, null, null, false);
        }

        // Normal damageable items keep durability.
        if (meta instanceof Damageable damageable) {
            return new StorageSign(mat, (short) damageable.getDamage(), 0, null, null, false);
        }

        // Normal items
        return new StorageSign(mat, (short) 0, 0, null, null, false);
    }

    private static ItemStack createHorseEggItem(int amount) {
        ItemStack horseEgg = new ItemStack(Material.GHAST_SPAWN_EGG, Math.max(1, amount));
        ItemMeta meta = horseEgg.getItemMeta();
        if (meta == null) return horseEgg;
        meta.setDisplayName("HorseEgg");
        meta.setLore(List.of(EMPTY_MARKER));
        horseEgg.setItemMeta(meta);
        return horseEgg;
    }

    @Override
    public String toString() {
        return "StorageSign{material=" + material + ", damage=" + damage
               + ", amount=" + amount + ", identifier=" + getIdentifier() + "}";
    }
}
