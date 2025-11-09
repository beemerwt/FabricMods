package com.github.beemerwt.mcrpg.event;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public record AllowDamageEvent(
    ServerPlayerEntity player,
    DamageSource damageSource,
    float amount
) implements GameEvent { }
