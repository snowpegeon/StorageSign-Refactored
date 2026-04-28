package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import wacky.storagesign.StorageSign;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Prevents players from opening the sign-editing GUI on an existing StorageSign.
 *
 * <p>This listener is registered only on <b>Paper</b> (which provides
 * {@code io.papermc.paper.event.player.PlayerOpenSignEvent} earlier in the lifecycle than
 * Spigot's {@code PlayerSignOpenEvent}).
 *
 * @see SpigotSignEditListener
 * @see SignEditListenerFactory
 */
public final class PaperSignEditListener implements Listener {

    private static final Logger LOG = Logger.getLogger(PaperSignEditListener.class.getName());

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(io.papermc.paper.event.player.PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
            LOG.finest("Cancelled sign edit (Paper) at " + block.getLocation());
        }
    }
}
