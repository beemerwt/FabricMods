package com.github.beemerwt.essence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public class NoclipCameraMixin {
    @Shadow @Nullable private Entity focusedEntity;

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void essence$noclipCamera(float desired, CallbackInfoReturnable<Float> cir) {
        if (focusedEntity instanceof PlayerEntity p && p.noClip) {
            // Keep the full desired distance — no push-in
            cir.setReturnValue(desired);
        }
    }
}
