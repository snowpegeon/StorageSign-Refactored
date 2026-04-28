package wacky.storagesign.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import wacky.storagesign.ConfigLoader;
import wacky.storagesign.StorageSign;

/**
 * Handles StorageSign crafting permission checks.
 */
public final class CraftListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCraft(CraftItemEvent event) {
        if (StorageSign.fromItemStack(event.getCurrentItem()) != null
            && !event.getWhoClicked().hasPermission("storagesign.craft")) {
            event.getWhoClicked().sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
        }
    }
}
