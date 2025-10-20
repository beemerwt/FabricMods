package com.github.beemerwt.mcrpg.extension;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public interface RandomTickExtension {
    void mcrpg$vanillaRandomTick(BlockState state, ServerWorld world, BlockPos pos, Random random);
}
