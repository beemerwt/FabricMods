package com.github.beemerwt.mcrpg.managers;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.abilities.GigaDrillBreaker;
import com.github.beemerwt.mcrpg.abilities.GreenTerra;
import com.github.beemerwt.mcrpg.abilities.SuperBreaker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.util.Messenger;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import com.github.beemerwt.mcrpg.util.TickScheduler;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import org.joml.Math;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityManager {
    /** A ready attempt: which ability was readied, and the expiry task. */
    private record ReadyAttempt(ActiveAbilityType activeAbilityType, TickScheduler.Task expiry) {}

    /** Track the current active ability and its scheduled deactivation task. */
    private record Active(ActiveAbilityType activeAbilityType, TickScheduler.Task task) {}

    private static final long READY_TIMEOUT_TICKS = 60L; // 3s at 20 ticks per second
    private static final Map<UUID, ReadyAttempt> readiedAbilities = new HashMap<>();
    private static final Map<UUID, Active> activeAbilities = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ActiveAbilityType, TickScheduler.Task>> cooldowns = new ConcurrentHashMap<>();

    private AbilityManager() {}

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            clearAllFor(id);
        });

        // Clean up on server stop.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            readiedAbilities.values().forEach(a -> safeCancel(a.expiry));
            activeAbilities.values().forEach(a -> safeCancel(a.task));
            cooldowns.values().forEach(map -> map.values().forEach(AbilityManager::safeCancel));
            readiedAbilities.clear();
            activeAbilities.clear();
            cooldowns.clear();
        });
    }

    /**
     * Ready an ability for activation. If the ability is already readied, attempt to activate it immediately.
     * @param player The player
     * @param activeAbilityType The ability to ready
     */
    public static void readyAbility(ServerPlayerEntity player, ActiveAbilityType activeAbilityType) {
        if (player == null || activeAbilityType == null) return;

        SkillConfig skillCfg = ConfigManager.whichSkillHasAbility(activeAbilityType).orElse(null);
        if (skillCfg == null) return;

        // Ensure the ability is enabled
        AbilityConfig abilityCfg = skillCfg.getAbilityConfig(activeAbilityType).orElse(null);
        if (abilityCfg == null || !abilityCfg.enabled) return;

        // use the same color for actionbar messages
        var textColor = NamedTextColor.NAMES.getOrDefault(skillCfg.bossbarColor, NamedTextColor.BLUE);
        String heldType = ItemClassifier.getItemType(player.getMainHandStack().getItem());

        long cdRemaining = cooldownRemaining(player, activeAbilityType);
        if (cdRemaining > 0L) {
            Messenger.actionBar(player, Component.text(activeAbilityType.getDisplayName(), textColor)
                    .append(Component.text(" is on cooldown! ("))
                    .append(Component.text((int)Math.ceil(cdRemaining / 20.0) + "s", NamedTextColor.YELLOW))
                    .append(Component.text(")")));
            return;
        }

        // Remove last alert if it exists (prevents overloading)
        var prev = readiedAbilities.remove(player.getUuid());
        if (prev != null) safeCancel(prev.expiry);

        // Schedule expiry
        var expiry = TickScheduler.schedule(READY_TIMEOUT_TICKS, () -> {
            ReadyAttempt attempt = readiedAbilities.get(player.getUuid());
            if (attempt != null && attempt.activeAbilityType == activeAbilityType) {
                Messenger.actionBar(player,
                        Component.text("You lower your ").append(Component.text(heldType, textColor))
                );
                readiedAbilities.remove(player.getUuid());
            }
        });

        readiedAbilities.put(player.getUuid(), new ReadyAttempt(activeAbilityType, expiry));

        Messenger.actionBar(player,
                Component.text("You ready your ").append(Component.text(heldType, textColor))
        );
    }

    public static void clearReadiedAbility(ServerPlayerEntity player) {
        if (player == null) return;
        var attempt = readiedAbilities.remove(player.getUuid());
        if (attempt != null) safeCancel(attempt.expiry);
    }

    /** The readied ability, if any (not validating cooldown here). */
    public static Optional<ActiveAbilityType> getReadiedAbility(ServerPlayerEntity player) {
        if (player == null) return Optional.empty();
        var attempt = readiedAbilities.get(player.getUuid());
        return attempt != null ? Optional.of(attempt.activeAbilityType) : Optional.empty();
    }

    /** Remaining cooldown in ticks for the given ability. */
    public static long cooldownRemaining(ServerPlayerEntity p, ActiveAbilityType a) {
        if (p == null || a == null) return 0L;
        var map = cooldowns.get(p.getUuid());
        if (map == null) return 0L;
        var task = map.get(a);
        return task != null ? Math.max(0L, task.getRemainingTicks()) : 0L;
    }

    public static boolean isActive(ServerPlayerEntity p, ActiveAbilityType a) {
        if (p == null || a == null) return false;
        var active = activeAbilities.get(p.getUuid());
        return active != null && active.activeAbilityType == a;
    }

    /** True only when no cooldown remains. */
    public static boolean canActivate(ServerPlayerEntity p, ActiveAbilityType a) {
        return cooldownRemaining(p, a) <= 0L;
    }

    // ---- lifecycle hooks called by manager ----
    /** Activate ability (handles duration, existing active replacement, and cooldown start). */
    public static void activate(ServerPlayerEntity player, ActiveAbilityType activeAbilityType) {
        if (player == null || activeAbilityType == null) return;

        // Clear the ready state on activation attempt (no lingering ready)
        var attempt = readiedAbilities.remove(player.getUuid());
        if (attempt != null) safeCancel(attempt.expiry);

        SkillConfig skillCfg = ConfigManager.whichSkillHasAbility(activeAbilityType).orElse(null);
        if (skillCfg == null) return;

        AbilityConfig abilityCfg = skillCfg.getAbilityConfig(activeAbilityType).orElse(null);
        if (abilityCfg == null || !abilityCfg.enabled) return;

        if (!(abilityCfg instanceof SuperAbilityConfig superAbility))
            return;

        int level = Leveling.getLevel(player, SkillType.MINING);
        if (level < superAbility.minLevel) return;

        long durationTicks = Leveling.getScaledTicks(superAbility.baseDuration, superAbility.maxDuration, level);
        long cooldownTicks = Leveling.getScaledTicks(superAbility.baseCooldown, superAbility.minCooldown, level);
        cooldownTicks += durationTicks; // cooldown starts after duration ends

        // If another duration ability is active for this player, cancel and cleanly deactivate it.
        var prior = activeAbilities.remove(player.getUuid());
        if (prior != null) {
            safeCancel(prior.task);
            // Ensure prior ability effect is removed if different
            if (prior.activeAbilityType != null) {
                try {
                    deactivate(player, prior.activeAbilityType);
                } catch (Throwable t) {
                    McRPG.getLogger().warning("Failed to deactivate prior ability {} for {}", prior.activeAbilityType, player.getName().getString(), t);
                }
            }
        }

        // Start effect for duration
        TickScheduler.Task endTask = TickScheduler.schedule(durationTicks, () -> {
            deactivate(player, activeAbilityType);
            activeAbilities.remove(player.getUuid());
        });

        activeAbilities.put(player.getUuid(), new Active(activeAbilityType, endTask));

        // Always start cooldown (instant abilities get cooldown too)
        startCooldown(player, activeAbilityType, cooldownTicks);

        // Apply effects
        switch (activeAbilityType) {
            case SUPER_BREAKER -> SuperBreaker.activateFor(player, level);
            case GIGA_DRILL_BREAKER -> GigaDrillBreaker.activateFor(player, level);
            case GREEN_TERRA -> GreenTerra.activateFor(player, level);
        }

        var color = NamedTextColor.NAMES.getOrDefault(skillCfg.bossbarColor, NamedTextColor.BLUE);
        Messenger.actionBar(player, Component.text(activeAbilityType.getDisplayName(), color).append(Component.text(" activated!")));

        SoundUtil.playSound(player, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), 1.0f, 1.0f);

        long now = player.getEntityWorld().getTime();
        McRPG.getLogger().debug("Activated {} for {} ticks ({} seconds) level={}, now={}, ends={}, cooldownEnds={}",
                activeAbilityType.getDisplayName(), durationTicks, durationTicks / 20.0f, level, now, now + durationTicks, now + cooldownTicks);
    }

    public static void deactivate(ServerPlayerEntity player, ActiveAbilityType activeAbilityType) {
        // Let effect expire naturally; we didn’t add attributes, just Haste
        // If you add attributes in the future, remove them here.
        switch (activeAbilityType) {
            case SUPER_BREAKER -> SuperBreaker.deactivateFor(player);
            case GIGA_DRILL_BREAKER -> GigaDrillBreaker.deactivateFor(player);
            case GREEN_TERRA -> GreenTerra.deactivateFor(player);
        }

        SoundUtil.playSound(player, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
    }

    /** Force-end the current active ability for this player (no-op if not active or mismatched). */
    public static void forceDeactivate(ServerPlayerEntity player, ActiveAbilityType activeAbilityType) {
        if (player == null || activeAbilityType == null) return;
        var active = activeAbilities.get(player.getUuid());
        if (active == null) return;
        // Only cancel if it matches the asked ability
        if (active.activeAbilityType == activeAbilityType) {
            safeCancel(active.task);
            activeAbilities.remove(player.getUuid());
            deactivate(player, activeAbilityType);
        }
    }

    // --- internals ---
    private static void startCooldown(ServerPlayerEntity player, ActiveAbilityType activeAbilityType, long cooldownTicks) {
        if (cooldownTicks <= 0) return;
        var map = cooldowns.computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>());

        // Cancel any existing cooldown for this ability before replacing it.
        var existing = map.get(activeAbilityType);
        if (existing != null) safeCancel(existing);

        TickScheduler.Task cd = TickScheduler.schedule(cooldownTicks, () -> {
            var cds = cooldowns.get(player.getUuid());
            if (cds != null) cds.remove(activeAbilityType);
        });

        map.put(activeAbilityType, cd);
    }

    public static void clearAllFor(UUID id) {
        if (id == null) return;

        var ready = readiedAbilities.remove(id);
        if (ready != null) safeCancel(ready.expiry);

        var active = activeAbilities.remove(id);
        if (active != null) safeCancel(active.task);

        var cds = cooldowns.remove(id);
        if (cds != null) cds.values().forEach(AbilityManager::safeCancel);
    }

    private static void safeCancel(TickScheduler.Task t) {
        try {
            if (t != null) t.cancel();
        } catch (Throwable ignored) {}
    }
}
