package wacky.storagesign;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import wacky.storagesign.config.StorageSignNBTConfig;
import wacky.storagesign.listener.BlockEventListener;
import wacky.storagesign.listener.CraftListener;
import wacky.storagesign.listener.EntityListener;
import wacky.storagesign.listener.InventoryListener;
import wacky.storagesign.listener.PlayerInteractListener;
import wacky.storagesign.listener.SignEditListenerFactory;
import wacky.storagesign.listener.SignPhysicsListener;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Main plugin class for StorageSign.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Load config and NBT data on enable</li>
 *   <li>Load ominous banner meta (for raid banner storage)</li>
 *   <li>Register crafting recipes for all current sign types</li>
 *   <li>Register all event listeners</li>
 * </ol>
 *
 * <p>All event handling logic lives in the {@code listener.*} package — this class stays slim.
 */
public final class StorageSignCore extends JavaPlugin {

    private static final Logger LOG = Logger.getLogger(StorageSignCore.class.getName());

    /**
     * The BannerMeta for the ominous/raid banner (WHITE_BANNER with 8 patterns).
     * Loaded from {@code storageSignNBT.yml} on enable.
     * Referenced statically by {@link StorageSign#getContents} and {@link StorageSign#isSimilar}.
     */
    public static BannerMeta ominousBannerMeta = null;

    @Override
    public void onEnable() {
        // ── 1. Config ─────────────────────────────────────────────────────────
        ConfigLoader.load(this);

        // ── 2. Ominous banner ─────────────────────────────────────────────────
        if (!ConfigLoader.getBannerDebug()) {
            loadOminousBanner();
        } else {
            getLogger().info("banner-debug=true: ominous banner loading skipped");
        }

        // ── 3. Crafting recipes ───────────────────────────────────────────────
        registerRecipes();

        // ── 4. Event listeners ────────────────────────────────────────────────
        registerListeners();

        getLogger().info("StorageSign enabled. Sign types: " + MaterialRegistry.SIGN_MATERIALS.size()
                         + ", Shulker types: " + MaterialRegistry.SHULKER_BOX_MATERIALS.size());
    }

    @Override
    public void onDisable() {
        getLogger().info("StorageSign disabled.");
    }

    // ── Ominous banner ─────────────────────────────────────────────────────────

    private void loadOminousBanner() {
        StorageSignNBTConfig nbtConfig = new StorageSignNBTConfig(this);
        if (!nbtConfig.isLoaded()) return;

        // Version key must match what storageSignNBT.yml uses: "1.21.1" (not full snapshot string)
        String version = Bukkit.getBukkitVersion().split("-")[0];
        String nbt = nbtConfig.getNbtString(version);
        if (nbt == null) {
            nbt = nbtConfig.getFirstNbtString();
        }
        if (nbt == null) {
            getLogger().warning("No NBT string found in storageSignNBT.yml for version: " + version);
            return;
        }

        try {
            // Deserialise NBT string into a WHITE_BANNER item and extract its BannerMeta
            ItemStack banner = deserializeBannerFromNbt(nbt);
            if (banner != null && banner.getItemMeta() instanceof BannerMeta bm) {
                ominousBannerMeta = bm;
                getLogger().info("Ominous banner meta loaded ("
                                 + bm.numberOfPatterns() + " patterns).");
            } else {
                getLogger().warning("Failed to parse ominous banner NBT for version: " + version);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Exception loading ominous banner", e);
        }
    }

    /**
     * Attempts to deserialise a banner from an SNBT text string.
     *
     * <p>Uses {@code ItemFactory.createItemStack(String)} — the same API as the original plugin.
     * This accepts the SNBT text format stored in {@code storageSignNBT.yml}.
     */
    private ItemStack deserializeBannerFromNbt(String nbt) {
        try {
            return Bukkit.getItemFactory().createItemStack(nbt);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to deserialize banner NBT via ItemFactory", e);
        }
        return null;
    }

    // ── Crafting recipes ───────────────────────────────────────────────────────

    /**
     * Registers a crafting recipe for each sign material.
     *
     * <p>Original compatible shape:
     * <pre>
     *   C C C
     *   C S C
     *   C H C
     * </pre>
     * where {@code H} is CHEST (normal) or ENDER_CHEST (hardrecipe=true).
     */
    private void registerRecipes() {
        for (Material signMat : MaterialRegistry.SIGN_MATERIALS) {
            NamespacedKey key = new NamespacedKey(this, "storagesign_" + signMat.name().toLowerCase());
            // Remove existing recipe with same key to avoid duplicates on reload
            Bukkit.removeRecipe(key);

            ItemStack result = StorageSign.createStorageSignItem(signMat, StorageSign.EMPTY_MARKER, 1);

            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("CCC", "CSC", "CHC");
            recipe.setIngredient('C', Material.CHEST);
            recipe.setIngredient('S', signMat);
            recipe.setIngredient('H', ConfigLoader.getHardrecipe() ? Material.ENDER_CHEST : Material.CHEST);
            recipe.setCategory(CraftingBookCategory.MISC);
            recipe.setGroup("StorageSign");
            Bukkit.addRecipe(recipe);
        }
        getLogger().info("Registered " + MaterialRegistry.SIGN_MATERIALS.size() + " StorageSign recipes.");
    }

    // ── Event listeners ────────────────────────────────────────────────────────

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new BlockEventListener(this), this);
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new EntityListener(), this);
        pm.registerEvents(new CraftListener(), this);
        SignEditListenerFactory.register(this);

        if (ConfigLoader.getNoBud()) {
            pm.registerEvents(new SignPhysicsListener(), this);
            getLogger().info("no-bud: BUD prevention enabled.");
        }
    }
}
