package com.github.beemerwt.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ScreenHandlerEvents {

    public interface BeforeSlotClick {
        /**
         * Called before a slot is clicked.
         * @param handler The screen handler.
         * @param world The server world.
         * @param player The player performing the click.
         * @param index The index of the slot that was clicked.
         */
        void beforeSlotClick(ScreenHandler handler,
                             ServerWorld world,
                             ServerPlayerEntity player,
                             int index);
    }

    public interface AfterSlotClick {
        /**
         * Called after a slot is clicked.
         * @param handler The screen handler.
         * @param world The server world.
         * @param player The player performing the click.
         * @param index The index of the slot that was clicked.
         */
        void afterSlotClick(ScreenHandler handler,
                            ServerWorld world,
                            ServerPlayerEntity player,
                            int index);
    }

    public interface BeforeQuickMove {
        /**
         * Called before a quick move (shift-click) is performed.
         * @param handler The screen handler.
         * @param world The server world.
         * @param player The player performing the quick move.
         * @param index The index of the slot that was quick-moved.
         */
        void beforeQuickMove(ScreenHandler handler,
                             ServerWorld world,
                             ServerPlayerEntity player,
                             int index);
    }

    public interface AfterQuickMove {
        /**
         * Called after a quick move (shift-click) is performed.
         * @param handler The screen handler.
         * @param world The server world.
         * @param player The player performing the quick move.
         * @param index The index of the slot that was quick-moved.
         */
        void afterQuickMove(ScreenHandler handler,
                            ServerWorld world,
                            ServerPlayerEntity player,
                            int index);
    }

    public interface OnClosed {
        /**
         * Called when a screen handler is closed.
         * @param handler The screen handler.
         * @param player The player closing the screen.
         */
        void onClosed(ScreenHandler handler, ServerPlayerEntity player);
    }

    public static final Event<BeforeSlotClick> BEFORE_SLOT_CLICK = EventFactory.createArrayBacked(
        BeforeSlotClick.class,
        (listeners) -> (slotIndex, button, actionType, callbackInfo) -> {
            for (BeforeSlotClick event : listeners) {
                event.beforeSlotClick(slotIndex, button, actionType, callbackInfo);
            }
        }
    );

    public static final Event<AfterSlotClick> AFTER_SLOT_CLICK = EventFactory.createArrayBacked(
        AfterSlotClick.class,
        (listeners) -> (slotIndex, button, actionType, callbackInfo) -> {
            for (AfterSlotClick event : listeners) {
                event.afterSlotClick(slotIndex, button, actionType, callbackInfo);
            }
        }
    );

    public static final Event<BeforeQuickMove> BEFORE_QUICK_MOVE = EventFactory.createArrayBacked(
        BeforeQuickMove.class,
        (listeners) -> (handler, world, player, index) -> {
            for (BeforeQuickMove event : listeners) {
                event.beforeQuickMove(handler, world, player, index);
            }
        }
    );

    public static final Event<AfterQuickMove> AFTER_QUICK_MOVE = EventFactory.createArrayBacked(
        AfterQuickMove.class,
        (listeners) -> (handler, world, player, index) -> {
            for (AfterQuickMove event : listeners) {
                event.afterQuickMove(handler, world, player, index);
            }
        }
    );

    public static final Event<OnClosed> CLOSED = EventFactory.createArrayBacked(
        OnClosed.class,
        (listeners) -> (handler, player) -> {
            for (OnClosed event : listeners) {
                event.onClosed(handler, player);
            }
        }
    );
}
