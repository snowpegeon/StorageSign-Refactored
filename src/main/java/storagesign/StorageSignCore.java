package storagesign;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import storagesign.config.StorageSignNBTConfig;
import storagesign.listener.BlockEventListener;
import storagesign.listener.CraftListener;
import storagesign.listener.EntityListener;
import storagesign.listener.InventoryListener;
import storagesign.listener.PlayerInteractListener;
import storagesign.listener.SignEditListenerFactory;
import storagesign.listener.SignPhysicsListener;
import storagesign.registry.MaterialRegistry;

/**
 * StorageSign プラグインのメインクラス。
 *
 * <p>要約:
 * <ol>
 *   <li>起動時に config および NBT データをロードする</li>
 *   <li>レイドバナーのメタをロードする</li>
 *   <li>全看板種別に対するクラフトレシピを登録する</li>
 *   <li>全イベントリスナーを登録する</li>
 * </ol>
 *
 * <p>イベント処理ロジックはすべて {@code listener.*} パッケージに割り当ててあり、このクラスはシンプルに保つ。
 */
public final class StorageSignCore extends JavaPlugin {

    private static final Logger LOG = Logger.getLogger(StorageSignCore.class.getName());

    /**
     * レイドバナー（白バナー パターン 8 枚）の BannerMeta。
     * 起動時に {@code storageSignNBT.yml} からロードする。
     * {@link StorageSign#getContents} および {@link StorageSign#isSimilar} から静的参照される。
     */
    public static BannerMeta ominousBannerMeta = null;

    @Override
    public void onEnable() {
        // ── 1. Config ロード ──────────────────────────────────────────────────────────────
        ConfigLoader.load(this);

        // ── 2. レイドバナー ───────────────────────────────────────────────────────────
        if (!ConfigLoader.getBannerDebug()) {
            loadOminousBanner();
        } else {
            getLogger().info("banner-debug=true: レイドバナーのロードをスキップしました");
        }

        // ── 3. クラフトレシピ ─────────────────────────────────────────────────────────
        registerRecipes();

        // ── 4. イベントリスナー ────────────────────────────────────────────────────────
        registerListeners();

        getLogger().info("StorageSign enabled. Sign types: " + MaterialRegistry.SIGN_MATERIALS.size()
                         + ", Shulker types: " + MaterialRegistry.SHULKER_BOX_MATERIALS.size());
    }

    @Override
    public void onDisable() {
        getLogger().info("StorageSign disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (!name.equalsIgnoreCase("storagesigngive") && !name.equalsIgnoreCase("ssgive")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
            return true;
        }

        if (!player.hasPermission("storagesign.give")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.sendMessage("§cこのコマンドはクリエイティブモードでのみ使用できます。");
            return true;
        }

        if (args.length < 2 || args.length > 3) {
            player.sendMessage("§e使い方: /" + label + " <itemIdentifier> <amount> [signType]");
            player.sendMessage("§7例: /" + label + " STONE 128 OAK_SIGN");
            return true;
        }

        String identifier = args[0].trim();
        int amount;
        try {
            amount = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§camount は整数で指定してください。");
            return true;
        }
        if (amount < 0) {
            player.sendMessage("§camount は 0 以上で指定してください。");
            return true;
        }

        Material signMaterial = resolveSignMaterial(args.length >= 3 ? args[2] : "OAK_SIGN");
        if (signMaterial == null || !MaterialRegistry.SIGN_MATERIALS.contains(signMaterial)) {
            player.sendMessage("§c看板種類が不正です。例: OAK_SIGN, SPRUCE_SIGN");
            return true;
        }

        StorageSign parsed = StorageSign.fromSignLines(
            new String[]{StorageSign.HEADER_LINE, identifier, Integer.toString(amount)}
        );
        if (parsed == null || parsed.isUnregistered()) {
            player.sendMessage("§citemIdentifier が不正です。");
            return true;
        }

        ItemStack ssItem = StorageSign.createStorageSignItem(signMaterial, parsed.getLoreText(), 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(ssItem);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage("§aStorageSign を付与しました: "
            + signMaterial.name() + " / " + parsed.getLoreText());
        return true;
    }

    private static Material resolveSignMaterial(String raw) {
        if (raw == null || raw.isBlank()) return Material.OAK_SIGN;

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        if (!normalized.endsWith("_SIGN")) {
            normalized = normalized + "_SIGN";
        }
        return Material.matchMaterial(normalized);
    }

    // ── レイドバナー ───────────────────────────────────────────────────────────────

    private void loadOminousBanner() {
        BannerMeta apiMeta = createOminousBannerMetaByApi();
        if (apiMeta != null) {
            ominousBannerMeta = apiMeta;
            getLogger().info("レイドバナーメタを API でロードしました ("
                             + apiMeta.numberOfPatterns() + " パターン)");
            return;
        }

        getLogger().warning("API でレイドバナー構築に失敗したため、SNBT フォールバックを試行します");

        StorageSignNBTConfig nbtConfig = new StorageSignNBTConfig(this);
        if (!nbtConfig.isLoaded()) return;

        // バージョンキーは storageSignNBT.yml の記込形式に合わせる: "1.21.1"(スナップショット文字列ではなく)
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
            // NBT 文字列から WHITE_BANNER アイテムをデシリアライズし、BannerMeta を取得する
            ItemStack banner = deserializeBannerFromNbt(nbt);
            if (banner != null && banner.getItemMeta() instanceof BannerMeta bm) {
                ominousBannerMeta = bm;
                getLogger().info("レイドバナーメタをロードしました ("
                                 + bm.numberOfPatterns() + " パターン)");
            } else {
                getLogger().warning("バージョン " + version + " のレイドバナー NBT のパースに失敗しました");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "レイドバナーのロード中に例外が発生しました", e);
        }
    }

    /**
     * API で不吉なバナー（白バナー 8 パターン）を構築する。
     * 成功時は BannerMeta、失敗時は null。
     */
    private BannerMeta createOminousBannerMetaByApi() {
        try {
            ItemStack banner = new ItemStack(Material.WHITE_BANNER);
            ItemMeta itemMeta = banner.getItemMeta();
            if (!(itemMeta instanceof BannerMeta bm)) return null;

            bm.setPatterns(java.util.List.of(
                createBannerPattern(DyeColor.CYAN, "RHOMBUS", "RHOMBUS_MIDDLE"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "STRIPE_BOTTOM"),
                createBannerPattern(DyeColor.GRAY, "STRIPE_CENTER"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "BORDER"),
                createBannerPattern(DyeColor.BLACK, "STRIPE_MIDDLE"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "HALF_HORIZONTAL"),
                createBannerPattern(DyeColor.LIGHT_GRAY, "CIRCLE", "CIRCLE_MIDDLE"),
                createBannerPattern(DyeColor.BLACK, "BORDER")
            ));
            return bm;
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "API 経由でレイドバナー構築に失敗しました", e);
        }
        return null;
    }

    private Pattern createBannerPattern(DyeColor color, String... candidateNames) {
        return new Pattern(color, resolvePatternType(candidateNames));
    }

    private PatternType resolvePatternType(String... candidateNames) {
        for (String candidateName : candidateNames) {
            try {
                return PatternType.valueOf(candidateName);
            } catch (IllegalArgumentException ignored) {
                // バージョン差分で enum 名が変わるため、候補を順に試す。
            }
        }
        throw new IllegalStateException(
            "Unsupported banner pattern type names: " + java.util.Arrays.toString(candidateNames)
        );
    }

    /**
     * SNBT 文字列からバナーをデシリアライズする。
     *
     * <p>{@code ItemFactory.createItemStack(String)} — 元プラグインと同じ API を使用。
     * {@code storageSignNBT.yml} に保存されている SNBT テキスト形式を受け入れる。
     */
    private ItemStack deserializeBannerFromNbt(String nbt) {
        try {
            return Bukkit.getItemFactory().createItemStack(nbt);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ItemFactory 経由でバナー NBT のデシリアライズに失敗しました", e);
        }
        return null;
    }

    // ── クラフトレシピ ──────────────────────────────────────────────────────────────

    /**
     * 各看板マテリアルに対するクラフトレシピを登録する。
     *
     * <p>元プラグイン互換のレシピ形状:
     * <pre>
     *   C C C
     *   C S C
     *   C H C
     * </pre>
     * C=CHEST、S=対象看板、H=CHEST（hardrecipe=true 時は ENDER_CHEST）
     */
    private void registerRecipes() {
        for (Material signMat : MaterialRegistry.SIGN_MATERIALS) {
            NamespacedKey key = new NamespacedKey(this, "storagesign_" + signMat.name().toLowerCase());
            // リロード時の重複登録を防ぐため、同じキーのレシピは一度削除する
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
        getLogger().info(MaterialRegistry.SIGN_MATERIALS.size() + " 種類の StorageSign レシピを登録しました。");
    }

    // ── イベントリスナー ────────────────────────────────────────────────────────────

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
            getLogger().info("no-bud: BUD 防止を有効化しました。");
        }
    }
}
