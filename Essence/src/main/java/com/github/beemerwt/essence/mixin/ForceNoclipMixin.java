package com.github.beemerwt.essence.mixin;

import com.github.beemerwt.essence.core.duck.INoclip;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class ForceNoclipMixin implements INoclip {
    @Unique private boolean essence$noclip = false;

    @Redirect(
        method = "tick()V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/PlayerEntity;noClip:Z",
            opcode = Opcodes.PUTFIELD
        )
    )
    private void essence$forceNoclip(PlayerEntity self, boolean value) {
        self.noClip = this.essence$noclip;
        if (self.noClip) {
            self.setOnGround(false);
            self.fallDistance = 0.0F;
        }
    }

    @Inject(method = "isSwimming", at = @At("HEAD"), cancellable = true)
    private void essence$noclip_isSwimming(CallbackInfoReturnable<Boolean> ci) {
        if (this.essence$noclip) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "canChangeIntoPose", at = @At("HEAD"), cancellable = true)
    private void essence$noclip_canChangeIntoPose(EntityPose pose, CallbackInfoReturnable<Boolean> ci) {
        if (pose == EntityPose.SWIMMING && this.essence$noclip) {
            ci.setReturnValue(false);
        }
    }

    @Override
    public boolean essence$isNoclip() {
        return essence$noclip;
    }

    @Override
    public void essence$setNoclip(boolean on) {
        this.essence$noclip = on;
    }
}
