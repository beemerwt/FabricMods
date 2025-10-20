package com.github.beemerwt.spawnertweaks.mixin;

import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.spawner.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access the current MobSpawnerEntry held by the logic. */
@Mixin(MobSpawnerLogic.class)
public interface MobSpawnerLogic_SpawnEntryAccessor {
    @Accessor("spawnEntry")
    MobSpawnerEntry st$getSpawnEntry();
}
