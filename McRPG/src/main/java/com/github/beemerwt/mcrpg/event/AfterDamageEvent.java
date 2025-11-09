package com.github.beemerwt.mcrpg.event;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public record AfterDamageEvent(
    ServerPlayerEntity player,
    DamageSource damageSource,
    float baseDamageTaken,
    float damageTaken,
    boolean blocked
) implements GameEvent {}
