package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Detects whether the server is running Paper and registers the appropriate sign-edit listener.
 *
 * <p>Paper provides {@code io.papermc.paper.event.player.PlayerOpenSignEvent}, which fires before
 * the sign GUI is shown. Spigot uses the standard {@code PlayerSignOpenEvent}.
 */
public final class SignEditListenerFactory {

    private static final Logger LOG = Logger.getLogger(SignEditListenerFactory.class.getName());

    /** Registers either {@link PaperSignEditListener} or {@link SpigotSignEditListener}. */
    public static void register(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        Listener listener;
        try {
            Class.forName("io.papermc.paper.event.player.PlayerOpenSignEvent");
            listener = new PaperSignEditListener();
            LOG.info("Registered Paper sign-edit listener");
        } catch (ClassNotFoundException e) {
            listener = new SpigotSignEditListener();
            LOG.info("Registered Spigot sign-edit listener");
        }
        pm.registerEvents(listener, plugin);
    }

    private SignEditListenerFactory() {}
}
