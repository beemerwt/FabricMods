package com.github.beemerwt.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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

    public interface BlockPlace {
        /**
         * Called after a block is placed in the world.
         * @param world the world the block is placed in
         * @param placer the entity placing the block, may be null
         * @param pos the position the block is placed at
         * @param state the block state being placed
         * @param stack the remaining item stack the block came from, may be empty
         */
        void onBlockPlace(World world, @Nullable LivingEntity placer,
                          BlockPos pos, BlockState state,
                          ItemStack stack);
    }

    public static final Event<ChangeSlot> CHANGE_SLOT = EventFactory.createArrayBacked(
            ChangeSlot.class,
            (listeners) -> (player, slot, oldStack, newStack) -> {
                for (ChangeSlot event : listeners) {
                    event.onChangeSlot(player, slot, oldStack, newStack);
                }
            }
    );

    /**
     * Called when a player interacts with an item (right-click).
     * More robust than UseItemCallback, as it triggers before the interaction is processed.
     * Cancelled upon SUCCESS or FAIL.
     * Can be used to cancel or modify the interaction.
     * If cancelled the server will resync the player's hand and armor slots to prevent desync issues.
     */
    public static final Event<InteractItem> INTERACT_ITEM = EventFactory.createArrayBacked(
            InteractItem.class,
            (listeners) -> (player, stack, hand) -> {
                for (InteractItem event : listeners) {
                    var result = event.onInteractItem(player, stack, hand);
                    if (result != ActionResult.PASS) return result;
                }

                return ActionResult.PASS;
            }
    );

    /**
     * Called after a block is placed in the world.
     */
    public static final Event<BlockPlace> BLOCK_PLACED = EventFactory.createArrayBacked(
            BlockPlace.class,
            (listeners) -> (world, placer, pos, state, stack) -> {
                for (BlockPlace event : listeners) {
                    event.onBlockPlace(world, placer, pos, state, stack);
                }
            }
    );
}
