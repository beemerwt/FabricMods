package com.github.beemerwt.mcrpg.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record AttackBlockEvent(
    ServerPlayerEntity player,
    ServerWorld world,
    Hand hand,
    BlockPos pos,
    Direction direction
) implements GameEvent {}
