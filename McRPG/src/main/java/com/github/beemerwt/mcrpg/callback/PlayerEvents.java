package com.github.beemerwt.mcrpg.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class PlayerEvents {

    public interface ChangeSlot {
        void onChangeSlot(ServerPlayerEntity player, EquipmentSlot slot, ItemStack oldStack, ItemStack newStack);
    }

    public interface InteractItem {
        /**
         * Called when a player interacts with an item (right-click).
         * Triggers before the call is processed.
         * @param player the player
         * @param stack the item stack being interacted with
         * @param hand the hand used (main or off)
         */
        ActionResult onInteractItem(ServerPlayerEntity player, ItemStack stack, Hand hand);
    }

    public static final Event<PlayerEvents.ChangeSlot> CHANGE_SLOT = EventFactory.createArrayBacked(
            PlayerEvents.ChangeSlot.class,
            (listeners) -> (player, slot, oldStack, newStack) -> {
                for (PlayerEvents.ChangeSlot event : listeners) {
                    event.onChangeSlot(player, slot, oldStack, newStack);
                }
            }
    );

    public static final Event<PlayerEvents.InteractItem> INTERACT_ITEM = EventFactory.createArrayBacked(
            PlayerEvents.InteractItem.class,
            (listeners) -> (player, stack, hand) -> {
                for (PlayerEvents.InteractItem event : listeners) {
                    var result = event.onInteractItem(player, stack, hand);
                    if (result != ActionResult.PASS) return result;
                }

                return ActionResult.PASS;
            }
    );
}
