package storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.sign.Side;

import storagesign.StorageSign;
import storagesign.registry.MaterialRegistry;

/**
 * 既存の StorageSign の看板編集 GUI の起動を防ぐ。
 *
 * <p><b>Spigot</b> 専用。Paper では代わりに {@link PaperSignEditListener} が登録される。
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
            LOG.finest("Spigot: 看板編集をキャンセル " + block.getLocation());
        }
    }
}
