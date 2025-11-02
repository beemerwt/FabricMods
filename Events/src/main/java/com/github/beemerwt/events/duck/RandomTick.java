package com.github.beemerwt.events.duck;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public interface RandomTick {
    void events$randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random);
}
