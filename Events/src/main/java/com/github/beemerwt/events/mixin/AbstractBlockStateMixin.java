package com.github.beemerwt.events.mixin;

import com.github.beemerwt.events.BlockEvents;
import com.github.beemerwt.events.duck.RandomTick;
import com.github.beemerwt.events.mixin.access.CropBlockInvoker;
import com.github.beemerwt.events.proxy.BlockStateProxy;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin implements RandomTick {

    @Shadow protected abstract BlockState asBlockState();
    @Shadow public abstract Block getBlock();

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

    @Inject(
        method = "randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V",
        at = @At("TAIL")
    )
    private void events$onRandomTick(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (REENTRANCY_GUARD.get() > 0) return; // already a re-entered vanilla call

        BlockState state = this.asBlockState();
        Block      block = this.getBlock();

        try {
            BlockStateProxy proxy = BlockStateProxy.obtain(block, state, pos);
            BlockEvents.RANDOM_TICK.invoker().onRandomTick(world, proxy, random);
        } finally {
            BlockStateProxy.release();
        }
    }
}
