package wacky.storagesign;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads and caches all values from {@code config.yml}.
 *
 * <p>All key names are kept identical to the original plugin's config so that existing
 * {@code config.yml} files continue to work without modification.
 *
 * <p>Note: the key {@code no-permisson} (missing 'i') is intentional — it matches the original.
 */
public final class ConfigLoader {

    private static final Logger LOG = Logger.getLogger(ConfigLoader.class.getName());

    // ── Config key constants ──────────────────────────────────────────────────
    private static final String KEY_NO_PERMISSION     = "no-permisson";  // typo in original — kept for compat
    private static final String KEY_LOG_LEVEL         = "log-level";
    private static final String KEY_MANUAL_IMPORT     = "manual-import";
    private static final String KEY_MANUAL_EXPORT     = "manual-export";
    private static final String KEY_AUTO_IMPORT       = "auto-import";
    private static final String KEY_AUTO_EXPORT       = "auto-export";
    private static final String KEY_AUTOCOLLECT       = "autocollect";
    private static final String KEY_HARDRECIPE        = "hardrecipe";
    private static final String KEY_DIVIDE_LIMIT      = "divide-limit";
    private static final String KEY_SNEAK_DIVIDE_LIMIT = "sneak-divide-limit";
    private static final String KEY_MAX_STACK_SIZE    = "max-stack-size";
    private static final String KEY_NO_BUD            = "no-bud";
    private static final String KEY_FALLING_BLOCK     = "falling-block-itemSS";
    private static final String KEY_BANNER_DEBUG      = "banner-debug";

    // ── Cached values ─────────────────────────────────────────────────────────
    private static String  noPermission;
    private static String  logLevel;
    private static boolean manualImport;
    private static boolean manualExport;
    private static boolean autoImport;
    private static boolean autoExport;
    private static boolean autocollect;
    private static boolean hardrecipe;
    private static int     divideLimit;
    private static int     sneakDivideLimit;
    private static int     maxStackSize;
    private static boolean noBud;
    private static boolean fallingBlockItemSS;
    private static boolean bannerDebug;

    private ConfigLoader() {}

    /**
     * Saves the default config if not present and loads all values.
     */
    public static void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        noPermission      = cfg.getString(KEY_NO_PERMISSION, "You do not have permission.");
        logLevel          = cfg.getString(KEY_LOG_LEVEL, "INFO");
        manualImport      = cfg.getBoolean(KEY_MANUAL_IMPORT, true);
        manualExport      = cfg.getBoolean(KEY_MANUAL_EXPORT, true);
        autoImport        = cfg.getBoolean(KEY_AUTO_IMPORT, true);
        autoExport        = cfg.getBoolean(KEY_AUTO_EXPORT, true);
        autocollect       = cfg.getBoolean(KEY_AUTOCOLLECT, true);
        hardrecipe        = cfg.getBoolean(KEY_HARDRECIPE, false);
        divideLimit       = cfg.getInt(KEY_DIVIDE_LIMIT, 1);
        sneakDivideLimit  = cfg.getInt(KEY_SNEAK_DIVIDE_LIMIT, 1);
        maxStackSize      = cfg.getInt(KEY_MAX_STACK_SIZE, 64);
        noBud             = cfg.getBoolean(KEY_NO_BUD, false);
        fallingBlockItemSS = cfg.getBoolean(KEY_FALLING_BLOCK, false);
        bannerDebug       = cfg.getBoolean(KEY_BANNER_DEBUG, false);

        // Apply log level to root plugin logger
        try {
            Level level = Level.parse(logLevel.toUpperCase());
            plugin.getLogger().setLevel(level);
        } catch (IllegalArgumentException e) {
            LOG.warning("Invalid log-level in config: " + logLevel + " — using INFO");
            plugin.getLogger().setLevel(Level.INFO);
        }

        LOG.fine("ConfigLoader loaded: auto-import=" + autoImport + ", auto-export=" + autoExport
                 + ", no-bud=" + noBud);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static String  getNoPermission()      { return noPermission;      }
    public static String  getLogLevel()          { return logLevel;          }
    public static boolean getManualImport()      { return manualImport;      }
    public static boolean getManualExport()      { return manualExport;      }
    public static boolean getAutoImport()        { return autoImport;        }
    public static boolean getAutoExport()        { return autoExport;        }
    public static boolean getAutocollect()       { return autocollect;       }
    public static boolean getHardrecipe()        { return hardrecipe;        }
    public static int     getDivideLimit()       { return divideLimit;       }
    public static int     getSneakDivideLimit()  { return sneakDivideLimit;  }
    public static int     getMaxStackSize()      { return maxStackSize;      }
    public static boolean getNoBud()             { return noBud;             }
    public static boolean getFallingBlockItemSS(){ return fallingBlockItemSS;}
    public static boolean getBannerDebug()       { return bannerDebug;       }
}
