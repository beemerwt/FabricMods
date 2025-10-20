package com.github.beemerwt.mcrpg.callback;

import com.github.beemerwt.mcrpg.proxies.AbstractFurnaceBlockEntityProxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class FurnaceEvents {

    public interface FuelConsumed {
        void onFuelConsumed(AbstractFurnaceBlockEntityProxy proxy, ItemStack fuel);
    }

    public interface SmeltedItem {
        void onSmeltedItem(AbstractFurnaceBlockEntityProxy proxy, ItemStack input, ItemStack output);
    }

    /**
     * An event that is called whenever fuel is consumed by a furnace.
     * This is always called BEFORE the fuel is decremented, but AFTER the fuelTime has increased.
     */
    public static final Event<FurnaceEvents.FuelConsumed> FUEL_CONSUMED = EventFactory.createArrayBacked(
            FurnaceEvents.FuelConsumed.class,
            (listeners) -> (proxy, fuel) -> {
                for (FurnaceEvents.FuelConsumed event : listeners) {
                    event.onFuelConsumed(proxy, fuel);
                }
            }
    );

    /**
     * An event that is called whenever an item is smelted by a furnace.
     * This is always called after the item is smelted.
     */
    public static final Event<FurnaceEvents.SmeltedItem> ITEM_SMELTED = EventFactory.createArrayBacked(
            FurnaceEvents.SmeltedItem.class,
            (listeners) -> (proxy, input, output) -> {
                for (FurnaceEvents.SmeltedItem event : listeners) {
                    event.onSmeltedItem(proxy, input, output);
                }
            }
    );
}
