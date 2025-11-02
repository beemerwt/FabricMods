package com.github.beemerwt.essence.mixin;

import com.github.beemerwt.essence.core.duck.INoclip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public class NoclipInteractionManagerMixin {

    // Override isFlyingLocked to always return true when noclip is active
    @Inject(method = "isFlyingLocked", at = @At("HEAD"), cancellable = true)
    private void essence$noclip_isFlyingLocked(CallbackInfoReturnable<Boolean> ci) {
        var mc = MinecraftClient.getInstance();
        var self = mc.player;
        if (self != null && ((INoclip)self).essence$isNoclip()) {
            ci.setReturnValue(true);
        }
    }
}
