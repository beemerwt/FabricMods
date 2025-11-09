package com.github.beemerwt.mcrpg.event;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

public record UseItemEvent(ServerPlayerEntity player, ServerWorld world, Hand hand) implements GameEvent {}
