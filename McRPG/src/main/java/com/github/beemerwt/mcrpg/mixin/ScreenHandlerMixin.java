package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.callback.ScreenHandlerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void mcrpg$capturePreClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ScreenHandlerEvents.BEFORE_SLOT_CLICK.invoker()
                .beforeSlotClick((ScreenHandler)(Object)this, sp.getEntityWorld(), sp, slotIndex);
    }

    @Inject(method = "onSlotClick", at = @At("TAIL"))
    private void mcrpg$updateOwnerAfterClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ScreenHandlerEvents.AFTER_SLOT_CLICK.invoker()
                .afterSlotClick((ScreenHandler)(Object)this, sp.getEntityWorld(), sp, slotIndex);
    }

    @Inject(method = "onClosed", at = @At("TAIL"))
    private void mcrpg$clearOwnerOnClose(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ScreenHandlerEvents.CLOSED.invoker().onClosed((ScreenHandler)(Object) this, sp);
    }
}
