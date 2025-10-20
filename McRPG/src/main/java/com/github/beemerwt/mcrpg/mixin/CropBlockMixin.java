package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.callback.CropBlockEvents;
import com.github.beemerwt.mcrpg.extension.RandomTickExtension;
import com.github.beemerwt.mcrpg.mixin.access.CropBlockInvoker;
import com.github.beemerwt.mcrpg.proxies.CropBlockProxy;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CropBlock.class)
public abstract class CropBlockMixin implements RandomTickExtension {

    @Unique
    private static final ThreadLocal<Integer> MCRPG_REENTRANCY = ThreadLocal.withInitial(() -> 0);

    @Override
    public void mcrpg$vanillaRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int depth = MCRPG_REENTRANCY.get();
        MCRPG_REENTRANCY.set(depth + 1);

        try {
            ((CropBlockInvoker)this).mcrpg$callRandomTick(state, world, pos, random);
        } finally {
            MCRPG_REENTRANCY.set(depth);
        }
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void mcrpg$boostGrowth(
            BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci
    ) {
        if (MCRPG_REENTRANCY.get() > 0) return; // already a re-entered vanilla call

        try {
            CropBlock self = (CropBlock) (Object) this;
            CropBlockProxy proxy = CropBlockProxy.obtain(state, pos, self);
            CropBlockEvents.RANDOM_TICK.invoker().onRandomTick(world, proxy, random);
        } finally {
            CropBlockProxy.release();
        }
    }
}
