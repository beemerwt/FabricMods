package com.github.beemerwt.spawnertweaks.mixin;

import com.github.beemerwt.spawnertweaks.duck.SpawnerIdHolder;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(MobSpawnerBlockEntity.class)
public abstract class MobSpawnerBlockEntityMixin implements SpawnerIdHolder {
    @Unique private UUID id;

    @Override
    public UUID getSpawnerId() {
        if (id == null) id = UUID.randomUUID();
        return id;
    }

    @Override
    public void setSpawnerId(UUID id) {
        this.id = id;
    }
}

