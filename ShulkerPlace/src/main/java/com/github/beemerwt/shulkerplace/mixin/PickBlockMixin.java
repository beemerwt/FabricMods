package com.github.beemerwt.shulkerplace.mixin;

import com.github.beemerwt.shulkerplace.client.PostPickRunner;
import com.mojang.logging.LogUtils;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class PickBlockMixin {

    @Inject(
        method = "pickItemFromBlock(Lnet/minecraft/util/math/BlockPos;Z)V",
        at = @At("TAIL")
    )
    public void pickItemFromBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
        LogUtils.getLogger().info("PickBlockMixin injected at tail of pickItemFromBlock");
        PostPickRunner.tryShulkerPickAfterVanilla();
    }
}
