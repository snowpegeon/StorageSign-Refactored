package wacky.storagesign.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads {@code storageSignNBT.yml} from the plugin data folder.
 *
 * <p>This file maps Minecraft version strings to NBT data strings used to reconstruct the
 * ominous/raid banner. It is separate from the main config to allow version-specific overrides
 * without requiring a plugin update.
 *
 * <p>The file is <em>not</em> copied from {@code resources/} — it is created externally (by the
 * server admin or the plugin installer) and must already exist in the plugin data folder.
 */
public final class StorageSignNBTConfig {

    private static final Logger LOG = Logger.getLogger(StorageSignNBTConfig.class.getName());

    private final YamlConfiguration config;
    private final boolean loaded;

    public StorageSignNBTConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "storageSignNBT.yml");
        YamlConfiguration cfg = null;
        boolean ok = false;
        if (file.exists()) {
            try {
                cfg = YamlConfiguration.loadConfiguration(file);
                ok = true;
                LOG.fine("Loaded storageSignNBT.yml from " + file.getAbsolutePath());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to load storageSignNBT.yml", e);
            }
        } else {
            LOG.info("storageSignNBT.yml not found — ominous banner will not be loaded");
        }
        this.config = cfg;
        this.loaded = ok;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the NBT string stored under the given Minecraft version key,
     * or {@code null} if not found.
     */
    public String getNbtString(String version) {
        if (config == null) return null;
        return config.getString(version);
    }

    /**
     * Returns the first non-null NBT string found in the file, regardless of version key.
     * Used as a fallback when the exact MC version key is not present.
     */
    public String getFirstNbtString() {
        if (config == null) return null;
        for (String key : config.getKeys(false)) {
            String val = config.getString(key);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }
}
