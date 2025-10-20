// mixin/MobSpawnerLogicMixin.java
package com.github.beemerwt.spawnertweaks.mixin;

import com.github.beemerwt.spawnertweaks.config.Config;
import com.github.beemerwt.spawnertweaks.config.ConfigManager;
import com.github.beemerwt.spawnertweaks.runtime.SpawnerCapTracker;
import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {

    @Shadow private int spawnDelay;
    @Shadow private int spawnCount;
    @Shadow private int maxNearbyEntities;
    @Shadow private int requiredPlayerRange;
    @Shadow private int spawnRange;
    @Shadow private int minSpawnDelay;
    @Shadow private int maxSpawnDelay;

    @Unique private static final ThreadLocal<String> ST_CURRENT_KEY = new ThreadLocal<>();

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void applyConfig(ServerWorld world, BlockPos pos, CallbackInfo ci) {
        final MobSpawnerEntry entry = ((MobSpawnerLogic_SpawnEntryAccessor)(Object)this).st$getSpawnEntry();
        final EntityType<?> type = entityTypeFromEntry(entry);
        final Config.SpawnSettings s = ConfigManager.effectiveSettings(type);

        final String key = makeSpawnerKey(world, pos);
        ST_CURRENT_KEY.set(key);

        if (s.spawnCap != -1) {
            int existing = SpawnerCapTracker.count(key);
            if (existing >= s.spawnCap) {
                this.spawnDelay = Math.max(this.spawnDelay, 200);
                ci.cancel();
                return;
            }
        }

        if (s.spawnCount != -1) this.spawnCount = s.spawnCount;
        if (s.maxNearbyEntities != -1) this.maxNearbyEntities = s.maxNearbyEntities;
        if (s.requiredPlayerRange != -1) this.requiredPlayerRange = s.requiredPlayerRange;
        if (s.spawnRange != -1) this.spawnRange = s.spawnRange;
        if (s.minSpawnDelay != -1) this.minSpawnDelay = s.minSpawnDelay;
        if (s.maxSpawnDelay != -1) this.maxSpawnDelay = s.maxSpawnDelay;
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private void clearThreadLocal(ServerWorld world, BlockPos pos, CallbackInfo ci) {
        ST_CURRENT_KEY.remove();
    }

    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnNewEntityAndPassengers(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean tagAndSpawn(ServerWorld world, Entity entity) {
        String key = ST_CURRENT_KEY.get();
        if (key != null) {
            entity.addCommandTag(SpawnerCapTracker.makeTag(key));
        }
        return world.spawnNewEntityAndPassengers(entity);
    }

    @Unique
    private static String makeSpawnerKey(ServerWorld world, BlockPos pos) {
        String dim = world.getRegistryKey().getValue().toString(); // e.g. minecraft:overworld
        return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Unique
    private EntityType<?> entityTypeFromEntry(MobSpawnerEntry entry) {
        if (entry == null) return null;
        String idStr = entry.getNbt().getString("id", "");
        if (idStr.isEmpty()) return null;

        Identifier id = Identifier.tryParse(idStr);
        if (id == null) return null;
        return Registries.ENTITY_TYPE.get(id);
    }
}