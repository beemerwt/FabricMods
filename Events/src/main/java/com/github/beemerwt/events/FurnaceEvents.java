package com.github.beemerwt.events;

import com.github.beemerwt.events.proxy.AbstractFurnaceBlockEntityProxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.ItemStack;

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
    public static final Event<FuelConsumed> FUEL_CONSUMED = EventFactory.createArrayBacked(
            FuelConsumed.class,
            (listeners) -> (proxy, fuel) -> {
                for (FuelConsumed event : listeners) {
                    event.onFuelConsumed(proxy, fuel);
                }
            }
    );

    /**
     * An event that is called whenever an item is smelted by a furnace.
     * This is always called after the item is smelted.
     */
    public static final Event<SmeltedItem> ITEM_SMELTED = EventFactory.createArrayBacked(
            SmeltedItem.class,
            (listeners) -> (proxy, input, output) -> {
                for (SmeltedItem event : listeners) {
                    event.onSmeltedItem(proxy, input, output);
                }
            }
    );
}
