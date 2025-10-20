package com.github.beemerwt.mcrpg.mixin.access;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Allows calling the protected randomTick method of CropBlock.
 * Must be separate from the mixin because we inject into randomTick.
 */
@Mixin(CropBlock.class)
public interface CropBlockInvoker {
    @Invoker("randomTick")
    void mcrpg$callRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random);
}
