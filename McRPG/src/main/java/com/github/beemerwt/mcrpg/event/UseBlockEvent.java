package com.github.beemerwt.mcrpg.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public record UseBlockEvent(
    ServerPlayerEntity player,
    ServerWorld world,
    Hand hand,
    @Nullable BlockHitResult hitResult)
    implements GameEvent {}
