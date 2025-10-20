package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.callback.ScreenHandlerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceScreenHandler.class)
public class AbstractFurnaceScreenHandlerMixin {
    @Inject(method = "quickMove", at = @At("HEAD"))
    private void mcrpg$capturePreQuickMove(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ScreenHandlerEvents.BEFORE_QUICK_MOVE.invoker()
                .beforeQuickMove((ScreenHandler)(Object)this, sp.getEntityWorld(), sp, index);
    }

    @Inject(method = "quickMove", at = @At("RETURN"))
    private void mcrpg$updateOwnerAfterQuickMove(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ScreenHandlerEvents.AFTER_QUICK_MOVE.invoker()
                .afterQuickMove((ScreenHandler)(Object)this, sp.getEntityWorld(), sp, index);
    }
}
