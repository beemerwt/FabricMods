package com.github.beemerwt.essence.mixin;

import com.github.beemerwt.essence.core.duck.INoclip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
abstract class ForceNoclipClientMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void essence$noclip_forceBeforeMove(CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        var self = (PlayerEntity)(Object)this;
        if (self == mc.player && ((INoclip)self).essence$isNoclip())
            self.noClip = true;
    }

    @Inject(method = "isSubmergedInWater", at = @At("HEAD"), cancellable = true)
    private void essence$noclip_isSubmergedInWater(CallbackInfoReturnable<Boolean> ci) {
        var mc = MinecraftClient.getInstance();
        var self = (PlayerEntity) (Object) this;
        if (self == mc.player && ((INoclip) self).essence$isNoclip()) {
            ci.setReturnValue(false);
        }
    }
}

