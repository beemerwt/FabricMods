package com.github.beemerwt.telekinesis.mixin;

import com.github.beemerwt.telekinesis.BreakZones;
import com.github.beemerwt.telekinesis.TeleContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.logging.Logger;

@Mixin(ServerWorld.class)
public abstract class ServerWorld_ItemSpawnMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void tk$interceptSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerWorld world = (ServerWorld)(Object)this;

        // --- ITEMS ---
        if (entity instanceof ItemEntity item) {
            if (TeleContext.DEBUG) {
                Logger.getLogger("Telekinesis").info("Item spawned: " + entity);
            }

            // Don’t vacuum a player’s own throw/drop
            if (TeleContext.isGuardedPlayerDrop(item)) return;

            ServerPlayerEntity breaker = BreakZones.breakerFor(world, item.getEntityPos());
            if (breaker != null) {
                if (TeleContext.vacuumTo(item, breaker)) {
                    // fully captured: cancel spawn
                    cir.setReturnValue(false);
                }
            }

            return;
        }

        // --- XP ORBS ---
        if (entity instanceof ExperienceOrbEntity orb) {
            if (TeleContext.DEBUG) {
                Logger.getLogger("Telekinesis").info("Orb spawned: " + entity);
            }

            ServerPlayerEntity credited = BreakZones.breakerFor(world, orb.getEntityPos());
            if (credited != null && !credited.isRemoved() && !credited.isDead()) {
                // Let vanilla handle pickup rules + Mending routing.
                // This credits XP and removes the orb.
                orb.onPlayerCollision(credited);

                // We’ve handled it; prevent the orb from spawning in the world.
                cir.setReturnValue(false);
            }
        }
    }
}