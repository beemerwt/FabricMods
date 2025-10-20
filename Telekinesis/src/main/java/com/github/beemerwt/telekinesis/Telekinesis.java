package com.github.beemerwt.telekinesis;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
public final class Telekinesis implements ModInitializer {
    private static final boolean SNEAK_DISABLES = false;
    private static final int LIFETIME_TICKS = 4;
    private static final double ZONE_RADIUS = 2.25;

    private static final ThreadLocal<Deque<AutoScope>> PENDING_POP =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public void onInitialize() {
        // On entity death, add a zone at the death position
        ServerLivingEntityEvents.AFTER_DEATH.register((living, source) -> {
            if (!(living.getEntityWorld() instanceof ServerWorld world)) return;
            ServerPlayerEntity killer = resolveKillerPlayer(world, source);
            if (killer == null || !shouldApplyTo(killer)) return;

            // Small, short-lived zone at the death spot.
            final double zoneRadius = 2.25;
            final int lifetimeTicks = 10;     // covers staggered loot spawns
            BreakZones.add(killer.getUuid(), living.getEntityPos(), zoneRadius, world.getTime(), lifetimeTicks);
        });

        // On block break, add a zone at the broken block position
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player == null || player.isSpectator() || player.isRemoved()) return;
            if (world.isClient()) return;

            // Short-lived zone right where the break happened
            BreakZones.add(player.getUuid(), Vec3d.ofCenter(pos), ZONE_RADIUS, world.getTime(), LIFETIME_TICKS);

            // Connected stacks: add vertical column of zones for a bounded range
            // Pick conservative ranges to avoid scanning forever on huge/modded stacks
            final int upMax = 64;   // tune to your liking
            final int downMax = 0;  // most stacks are above the base
            if (isColumnStack(state.getBlock().getTranslationKey())) {
                BreakZones.addColumnZones((ServerWorld)world, player.getUuid(), pos, upMax, downMax, LIFETIME_TICKS, 2.25);
            }
        });

        // purge zones every tick
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getWorlds().forEach(w -> BreakZones.purgeExpired(w.getTime()))
        );
    }

    private static boolean isColumnStack(String translationKey) {
        // Cheap string checks to avoid hard deps; tune as needed
        // (You can switch this to instanceof checks on Blocks.* if you’d rather)
        return translationKey.contains("bamboo")
                || translationKey.contains("scaffolding")
                || translationKey.contains("sugar_cane")
                || translationKey.contains("cactus")
                || translationKey.contains("kelp")
                || translationKey.contains("twisting_vines")
                || translationKey.contains("weeping_vines");
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


    private record AutoScope(TeleContext scope) implements AutoCloseable {
        @Override public void close() { if (scope != null) scope.close(); }
    }
}
