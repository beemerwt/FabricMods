package com.github.beemerwt.spawnertweaks.mixin;

import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

