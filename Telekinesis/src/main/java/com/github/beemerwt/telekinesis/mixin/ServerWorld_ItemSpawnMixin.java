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

            if (item.getCommandTags().contains(TeleContext.TK_BYPASS_TAG)) return;

            // Don’t vacuum a player’s own throw/drop
            if (TeleContext.isGuardedPlayerDrop(item)) return;

            // 1) During active BEFORE..AFTER session: buffer
            if (BreakZones.bufferIfActive(world, item)) {
                cir.setReturnValue(false);
                return;
            }

            // 2) AFTER commit (same tick): linger vacuum
            if (BreakZones.tryLingerCapture(world, item)) {
                cir.setReturnValue(false);
                return;
            }

            // 3) block cascade linger (multi-tick)
            if (BreakZones.tryColumnCapture(world, item)) {
                cir.setReturnValue(false);
                return;
            }

            // 4) mob linger (multi-tick TTL)
            if (BreakZones.tryMobLingerCapture(world, item)) {
                cir.setReturnValue(false);
                return;
            }

            return;
        }

        // --- XP ORBS ---
        if (entity instanceof ExperienceOrbEntity orb) {
            if (TeleContext.DEBUG) {
                Logger.getLogger("Telekinesis").info("Orb spawned: " + entity);
            }

            // 1) buffer XP during session
            if (BreakZones.bufferXpIfActive(world, orb)) {
                cir.setReturnValue(false);
                return;
            }

            // 2) linger credit in same tick after commit
            if (BreakZones.tryLingerCreditXp(world, orb)) {
                cir.setReturnValue(false);
                return;
            }

            // 4) mob linger (multi-tick TTL)
            if (BreakZones.tryMobLingerCreditXp(world, orb)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}