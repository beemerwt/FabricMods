package com.github.beemerwt.events.mixin;

import com.github.beemerwt.events.CropBlockEvents;
import com.github.beemerwt.events.duck.RandomTick;
import com.github.beemerwt.events.mixin.access.CropBlockInvoker;
import com.github.beemerwt.events.proxy.CropBlockProxy;
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
public abstract class CropBlockMixin implements RandomTick {

    @Unique
    private static final ThreadLocal<Integer> REENTRANCY_GUARD = ThreadLocal.withInitial(() -> 0);

    @Override
    public void events$randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int depth = REENTRANCY_GUARD.get();
        REENTRANCY_GUARD.set(depth + 1);

        try {
            ((CropBlockInvoker)this).events$callRandomTick(state, world, pos, random);
        } finally {
            REENTRANCY_GUARD.set(depth);
        }
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void events$boostGrowth(
            BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci
    ) {
        if (REENTRANCY_GUARD.get() > 0) return; // already a re-entered vanilla call

        try {
            CropBlock self = (CropBlock) (Object) this;
            CropBlockProxy proxy = CropBlockProxy.obtain(state, pos, self);
            CropBlockEvents.RANDOM_TICK.invoker().onRandomTick(world, proxy, random);
        } finally {
            CropBlockProxy.release();
        }
    }
}
