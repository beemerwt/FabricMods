package com.github.beemerwt.mcrpg.managers;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.ability.SuperAbility;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;
import com.github.beemerwt.mcrpg.ability.GigaDrillBreaker;
import com.github.beemerwt.mcrpg.ability.GreenTerra;
import com.github.beemerwt.mcrpg.ability.SuperBreaker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.event.UseBlockEvent;
import com.github.beemerwt.mcrpg.event.UseItemEvent;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.*;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityManager {
    private static final Map<SkillType, SuperAbilityRuntime> abilityRuntimes = new EnumMap<>(SkillType.class);

    private AbilityManager() {}

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            clearAllFor(id);
        });
    }

    private static void clearAllFor(UUID playerId) {
        var runtimeEntries = abilityRuntimes.values();
        for (var runtime : runtimeEntries) {
            runtime.clearPlayerData(playerId);
        }
    }

    public static SuperAbilityRuntime getAbilityRuntime(SuperAbility<?, ?> ability) {
        var skillType = ability.getSkillType();
        return abilityRuntimes.computeIfAbsent(skillType, k -> new SuperAbilityRuntime(TickClock::now));
    }
}
