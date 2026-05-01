package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import storagesign.ConfigLoader;

class InventoryListenerTest {

    @BeforeEach
    void resetConfigFlags() throws Exception {
        setConfigFlag("autoImport", true);
        setConfigFlag("autoExport", true);
        setConfigFlag("unregisterOnEmpty", true);
    }

    @Test
    void onItemMove_bothAutoFlagsDisabled_skipsWithoutError() throws Exception {
        setConfigFlag("autoImport", false);
        setConfigFlag("autoExport", false);

        InventoryListener listener = new InventoryListener(null);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(
            mock(Inventory.class),
            new ItemStack(Material.STONE, 1),
            mock(Inventory.class),
            true
        );

        assertDoesNotThrow(() -> listener.onItemMove(event));
    }

    @Test
    void onBlockDispense_supportedSourceSynchronizesStorageSignAmount() {
        InventoryListener listener = new InventoryListener(null);

        Block dispenser = mock(Block.class);
        when(dispenser.getType()).thenReturn(Material.DISPENSER);

        ItemStack dispensed = mock(ItemStack.class);
        when(dispensed.getAmount()).thenReturn(5);
        when(dispensed.getType()).thenReturn(Material.BEE_NEST);
        when(dispensed.clone()).thenReturn(dispensed);

        SignSide signFront = mock(SignSide.class);
        Sign signState = mock(Sign.class);
        Block signBlock = mock(Block.class);

        when(signFront.getLines()).thenReturn(new String[] {
            "StorageSign", "BEE_NEST", "12", "0LC 0s 12"
        });
        when(signState.getSide(Side.FRONT)).thenReturn(signFront);
        when(signBlock.getType()).thenReturn(Material.OAK_SIGN);
        when(signBlock.getState()).thenReturn(signState);

        Block downBlock = mock(Block.class);
        when(downBlock.getType()).thenReturn(Material.AIR);

        when(dispenser.getRelative(BlockFace.UP)).thenReturn(signBlock);
        when(dispenser.getRelative(BlockFace.DOWN)).thenReturn(downBlock);

        BlockDispenseEvent event = new BlockDispenseEvent(
            dispenser,
            dispensed,
            new Vector(0, 0, 0)
        );

        assertDoesNotThrow(() -> listener.onBlockDispense(event));

        verify(signFront).setLine(2, "7");
        verify(signState).update();
    }

    @Test
    void onBlockDispense_unsupportedSourceIsIgnored() {
        InventoryListener listener = new InventoryListener(null);

        Block furnace = mock(Block.class);
        when(furnace.getType()).thenReturn(Material.FURNACE);

        BlockDispenseEvent event = new BlockDispenseEvent(
            furnace,
            new ItemStack(Material.STONE, 3),
            new Vector(0, 0, 0)
        );

        assertDoesNotThrow(() -> listener.onBlockDispense(event));
    }

    private static void setConfigFlag(String fieldName, boolean value) throws Exception {
        Field f = ConfigLoader.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.setBoolean(null, value);
    }
}
