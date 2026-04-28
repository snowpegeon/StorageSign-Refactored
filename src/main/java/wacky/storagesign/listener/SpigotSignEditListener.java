package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.sign.Side;

import wacky.storagesign.StorageSign;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Prevents players from opening the sign-editing GUI on an existing StorageSign.
 *
 * <p>This listener is registered only on <b>Spigot</b>. On Paper, {@link PaperSignEditListener}
 * is registered instead (it uses the earlier {@code PlayerOpenSignEvent}).
 *
 * @see PaperSignEditListener
 * @see SignEditListenerFactory
 */
public final class SpigotSignEditListener implements Listener {

    private static final Logger LOG = Logger.getLogger(SpigotSignEditListener.class.getName());

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(org.bukkit.event.player.PlayerSignOpenEvent event) {
        Block block = event.getSign().getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
            LOG.finest("Cancelled sign edit (Spigot) at " + block.getLocation());
        }
    }
}
