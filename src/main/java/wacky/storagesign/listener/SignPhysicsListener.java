package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import wacky.storagesign.StorageSign;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Cancels {@link BlockPhysicsEvent} for StorageSign blocks.
 *
 * <p>This prevents BUD (Block Update Detector) behaviour where a block state change adjacent to
 * a sign would normally trigger a physics update that can cause the sign to pop off.
 *
 * <p>Only registered when {@code no-bud: true} is set in {@code config.yml}.
 */
public final class SignPhysicsListener implements Listener {

    private static final Logger LOG = Logger.getLogger(SignPhysicsListener.class.getName());

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!MaterialRegistry.isAnySign(block.getType())) return;
        if (StorageSign.isStorageSign(block)) {
            event.setCancelled(true);
        }
    }
}
