package com.github.beemerwt.mcrpg.event;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public record BreakBlockEvent(
    ServerWorld world,
    ServerPlayerEntity player,
    Block block,
    BlockPos pos,
    BlockState state,
    @Nullable BlockEntity be,
    ItemStack tool
) implements GameEvent {}
