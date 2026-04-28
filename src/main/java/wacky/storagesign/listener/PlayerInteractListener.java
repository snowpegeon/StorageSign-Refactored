package wacky.storagesign.listener;

import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import wacky.storagesign.ConfigLoader;
import wacky.storagesign.StorageSign;
import wacky.storagesign.StorageSignCore;
import wacky.storagesign.registry.DyeRegistry;
import wacky.storagesign.registry.MaterialRegistry;

/**
 * Player interaction handler aligned with original StorageSign behavior.
 *
 * <p>Core semantics:
 * <ul>
 *   <li>Interact on SS is right-click driven (import/export/register/merge/divide).</li>
 *   <li>Off-hand interaction is ignored except for placement prevention while sneaking.</li>
 *   <li>Dye/ink interactions are delegated to vanilla sign behavior.</li>
 * </ul>
 */
public final class PlayerInteractListener implements Listener {

    private static final Logger LOG = Logger.getLogger(PlayerInteractListener.class.getName());

    private static final Material INK_SAC = Material.INK_SAC;
    private static final Material GLOW_INK_SAC = Material.GLOW_INK_SAC;

    public PlayerInteractListener(StorageSignCore plugin) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Spectators cannot interact with StorageSigns
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = event.getClickedBlock();
        if (block == null && event.getAction() == Action.RIGHT_CLICK_AIR
            && event.useInteractedBlock() == Result.DENY) {
            block = player.getTargetBlockExact(3);
        }
        if (block == null || !MaterialRegistry.isAnySign(block.getType())) return;

        StorageSign ss = StorageSign.fromBlock(block);
        if (ss == null) return;

        // Off-hand: prevent accidental SS placement while sneaking, then ignore.
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack offHand = event.getItem();
            if (player.isSneaking() && StorageSign.isStorageSign(offHand)) {
                event.setUseItemInHand(Result.DENY);
                event.setUseInteractedBlock(Result.DENY);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);

        if (!player.hasPermission("storagesign.use")) {
            player.sendMessage("§c" + ConfigLoader.getNoPermission());
            event.setCancelled(true);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) hand = new ItemStack(Material.AIR);
        Material handMat = hand.getType();

        // Empty SS: register held item as target type.
        if (ss.isEmpty()) {
            registerItem(player, block, hand);
            return;
        }

        // Hand has a StorageSign item (merge/split/sign-item storage flows).
        StorageSign handSS = StorageSign.fromItemStack(hand);
        if (handSS != null) {
            processStorageSignItemInteraction(player, block, ss, hand, handSS);
            return;
        }

        // Manual import (matching item in hand / inventory).
        if (ss.isSimilar(hand)) {
            importItems(player, block, ss, hand);
            return;
        }

        // Manual export fallback.
        if (!ConfigLoader.getManualExport()) return;

        // Dye / ink should use vanilla behavior.
        if (DyeRegistry.isDye(handMat) || isSac(handMat)) {
            event.setUseItemInHand(Result.ALLOW);
            event.setUseInteractedBlock(Result.ALLOW);
            return;
        }

        exportItems(player, block, ss);
    }

    private void registerItem(Player player, Block block, ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) return;

        StorageSign newSS = StorageSign.fromStoredItem(hand);
        if (newSS == null) return;

        // Original behavior: registration only sets item type; it does not consume held items.
        applyToBlock(block, newSS);
    }

    private void processStorageSignItemInteraction(Player player, Block block, StorageSign blockSS,
                                                   ItemStack handItem, StorageSign handSS) {
        // Merge full SS item stack into sign.
        if (!handSS.isEmpty() && ConfigLoader.getManualImport()) {
            ItemStack handContents = handSS.getContents(1);
            if (handContents != null && blockSS.isSimilar(handContents)) {
                long add = (long) handSS.getAmount() * Math.max(1, handItem.getAmount());
                if (add <= Integer.MAX_VALUE - blockSS.getAmount()) {
                    blockSS.setAmount((int) (blockSS.getAmount() + add));
                    player.getInventory().setItemInMainHand(
                        StorageSign.createStorageSignItem(handItem.getType(), StorageSign.EMPTY_MARKER, handItem.getAmount())
                    );
                    applyToBlock(block, blockSS);
                }
                return;
            }
        }

        // Store empty signs into "sign-in-sign" StorageSign.
        if (handSS.isEmpty() && ConfigLoader.getManualImport()
            && blockSS.isSignAsItem() && blockSS.getMaterial() == handItem.getType()) {
            int added = 0;
            if (player.isSneaking()) {
                added = handItem.getAmount();
                player.getInventory().clear(player.getInventory().getHeldItemSlot());
            } else {
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item == null || item.getType() != handItem.getType()) continue;
                    StorageSign itemSS = StorageSign.fromItemStack(item);
                    if (itemSS == null || !itemSS.isEmpty()) continue;
                    added += item.getAmount();
                    player.getInventory().clear(i);
                }
            }
            if (added > 0) {
                blockSS.setAmount(blockSS.getAmount() + added);
                applyToBlock(block, blockSS);
            }
            return;
        }

        // Divide block SS into held empty SS stack.
        if (handSS.isEmpty() && ConfigLoader.getManualExport()
            && blockSS.getAmount() > handItem.getAmount()) {
            ItemStack template = blockSS.getContents(1);
            if (template == null) return;

            StorageSign divided = StorageSign.fromStoredItem(template);
            if (divided == null) return;

            int signsInHand = Math.max(1, handItem.getAmount());
            int limit = player.isSneaking() ? ConfigLoader.getSneakDivideLimit() : ConfigLoader.getDivideLimit();
            int perSign;
            if (limit > 0 && blockSS.getAmount() > limit * (signsInHand + 1)) {
                perSign = limit;
            } else {
                perSign = blockSS.getAmount() / (signsInHand + 1);
            }
            if (perSign <= 0) return;

            divided.setAmount(perSign);
            player.getInventory().setItemInMainHand(
                StorageSign.createStorageSignItem(handItem.getType(), divided.getLoreText(), signsInHand)
            );
            blockSS.setAmount(blockSS.getAmount() - (perSign * signsInHand));
            applyToBlock(block, blockSS);
        }
    }

    private void importItems(Player player, Block block, StorageSign ss, ItemStack hand) {
        if (!ConfigLoader.getManualImport()) return;

        if (player.isSneaking()) {
            int add = hand.getAmount();
            ss.setAmount(ss.getAmount() + add);
            player.getInventory().clear(player.getInventory().getHeldItemSlot());

            // Apply dye color / ink glow AND sign lines to the same Sign object in one update.
            if (block.getState() instanceof Sign sign) {
                if (DyeRegistry.isDye(hand.getType())) {
                    DyeColor color = DyeRegistry.getColor(hand.getType());
                    if (color != null) sign.getSide(Side.FRONT).setColor(color);
                } else if (isSac(hand.getType())) {
                    sign.getSide(Side.FRONT).setGlowingText(isGlowSac(hand.getType()));
                }
                ss.applyToSign(sign);  // applies lines + sign.update() in one call
            }
        } else {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (!ss.isSimilar(item)) continue;
                ss.setAmount(ss.getAmount() + item.getAmount());
                player.getInventory().clear(i);
            }
            applyToBlock(block, ss);
        }
        player.updateInventory();
    }

    private void exportItems(Player player, Block block, StorageSign ss) {
        if (ss.isEmpty() || ss.getAmount() <= 0) return;

        ItemStack out = ss.getContents(1);
        if (out == null) return;

        int max = out.getMaxStackSize();
        if (player.isSneaking()) {
            out.setAmount(1);
            ss.setAmount(ss.getAmount() - 1);
        } else if (ss.getAmount() > max) {
            out.setAmount(max);
            ss.setAmount(ss.getAmount() - max);
        } else {
            out.setAmount(ss.getAmount());
            ss.setAmount(0);
        }

        Location dropLoc = player.getLocation().clone().add(0, 0.5, 0);
        player.getWorld().dropItem(dropLoc, out);
        applyToBlock(block, ss);
    }

    private static boolean isSac(Material mat) {
        return mat == INK_SAC || mat == GLOW_INK_SAC;
    }

    private static boolean isGlowSac(Material mat) {
        return mat == GLOW_INK_SAC;
    }

    private static void applyToBlock(Block block, StorageSign ss) {
        if (block.getState() instanceof Sign sign) {
            ss.applyToSign(sign);
        }
    }
}
