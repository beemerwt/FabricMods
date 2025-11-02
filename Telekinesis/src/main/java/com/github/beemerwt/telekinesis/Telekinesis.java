package com.github.beemerwt.telekinesis;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: Check offhand slot to stack into as well

public final class Telekinesis implements ModInitializer {
    private static final boolean SNEAK_DISABLES = false;

    @Override
    public void onInitialize() {
        // On entity death, add a zone at the death position
        ServerLivingEntityEvents.AFTER_DEATH.register((living, source) -> {
            if (!(living.getEntityWorld() instanceof ServerWorld world)) return;
            ServerPlayerEntity killer = resolveKillerPlayer(world, source);
            if (killer == null || !shouldApplyTo(killer)) return;

            // Small, short-lived zone at the death spot.
            BreakZones.addMobKillZone(killer, world, living.getEntityPos());
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerWorld sw)) return true;
            if (!(player instanceof ServerPlayerEntity sp)) return true;
            BreakZones.onBefore(sw, sp, pos); // your existing session start
            return true; // allow breaking
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return;

            // Commit same-tick buffered items/xp
            BreakZones.onAfter(sp);
        });

        // If the break gets cancelled
        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity sp) BreakZones.onCancel(sp);
        });

        // purge zones every tick
        ServerTickEvents.END_SERVER_TICK.register(server -> BreakZones.endOfTickCleanup());
    }

    private static boolean shouldApplyTo(ServerPlayerEntity p) {
        return !SNEAK_DISABLES || !p.isSneaking();
    }

    private static ServerPlayerEntity resolveKillerPlayer(ServerWorld world, DamageSource source) {
        if (source == null) return null;

        // 1) Direct player hit
        if (source.getAttacker() instanceof ServerPlayerEntity sp) return sp;

        // 2) Projectile shot by player
        if (source.getAttacker() instanceof ProjectileEntity proj) {
            if (proj.getOwner() instanceof ServerPlayerEntity owner) return owner;
        }

        // 3) TNT primed by player
        if (source.getAttacker() instanceof TntEntity tnt) {
            if (tnt.getOwner() instanceof ServerPlayerEntity owner) return owner;
        }

        // 4) Tamed pet belonging to a player
        if (source.getAttacker() instanceof Tameable tame) {
            if (tame.getOwner() instanceof ServerPlayerEntity owner) return owner;
        }

        return null;
    }
}
