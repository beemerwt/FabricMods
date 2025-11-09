package com.github.beemerwt.mcrpg.event;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record PlaceBlockEvent(
    ServerWorld world,
    ServerPlayerEntity player,
    BlockPos pos,
    BlockState state,
    ItemStack stack
) implements GameEvent {}
