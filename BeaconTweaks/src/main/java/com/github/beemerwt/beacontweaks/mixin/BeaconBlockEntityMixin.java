package com.github.beemerwt.beacontweaks.mixin;

import com.github.beemerwt.beacontweaks.BeaconTweaks;
import com.github.beemerwt.beacontweaks.BTConfig;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    // Yarn keeps this method stable; we shadow to read the beacon's internal tick counter.
    @Shadow private int level;

    @Inject(method = "applyPlayerEffects", at = @At("HEAD"), cancellable = true)
    private static void onApplyPlayerEffects(World world,
                                             BlockPos pos,
                                             int beaconLevel,
                                             @Nullable RegistryEntry<StatusEffect> primaryEffect,
                                             @Nullable RegistryEntry<StatusEffect> secondaryEffect,
                                             CallbackInfo ci)
    {
        if (!(world instanceof ServerWorld serverWorld)) return;

        // We fully replace vanilla behavior so future code doesn't double-apply.
        ci.cancel();

        final BTConfig cfg = BeaconTweaks.CONFIG;

        // Respect a configurable interval. We piggyback on world time; vanilla uses 80.
        if ((world.getTime() % Math.max(1, cfg.tickInterval)) != 0) {
            return;
        }

        if (primaryEffect == null && (!cfg.includeSecondary || secondaryEffect == null))
            return;

        final int radius = computeRadius(beaconLevel, cfg);
        final int duration = Math.max(20, cfg.tickInterval + 40);

        int ampPrimary = cfg.boostAmplifier ? Math.max(0, beaconLevel - 1) : 0;
        int ampSecondary = cfg.boostAmplifier ? Math.max(0, beaconLevel - 1) : 0;

        final boolean upgradeToLvl2 = cfg.includeSecondary
                && primaryEffect != null
                && primaryEffect.equals(secondaryEffect)
                && beaconLevel >= 4;

        if (upgradeToLvl2) {
            // Ensure at least amplifier 1 (Level II). If boostAmplifier makes it higher, keep the higher one.
            ampPrimary = Math.max(ampPrimary, 1);
            // Do NOT apply a separate secondary in this case.
            secondaryEffect = null;
        }

        // Gather players within radius (horizontal) and build height (vertical), same as vanilla semantics.
        final List<ServerPlayerEntity> players = serverWorld.getPlayers(p ->
                !p.isSpectator() && p.squaredDistanceTo(pos.toCenterPos()) <= (double)(radius * radius));

        for (ServerPlayerEntity p : players) {
            if (primaryEffect != null)
                p.addStatusEffect(new StatusEffectInstance(primaryEffect, duration, ampPrimary, true, true));

            // Only apply separate secondary if it differs from primary
            if (cfg.includeSecondary && secondaryEffect != null && !secondaryEffect.equals(primaryEffect))
                p.addStatusEffect(new StatusEffectInstance(secondaryEffect, duration, ampSecondary, true, true));
        }
    }

    private static int computeRadius(int level, BTConfig cfg) {
        // Forced global radius takes precedence.
        if (cfg.forceAllBeaconsRange >= 0) return cfg.forceAllBeaconsRange;

        // Fixed constants by beacon level.
        // index 0 unused for clarity.
        final int[] R = {0, 9, 34, 83, 164};
        int clamped = Math.max(0, Math.min(level, 4));
        return R[clamped];
    }
}
