package com.github.beemerwt.spawnertweaks.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(BlockEntity.class)
public abstract class BlockEntity_ReadWriteMixin {

    // Called for ALL block entities; gate to spawners at runtime
    @Inject(
            method = "read(Lnet/minecraft/storage/ReadView;)V",
            at = @At("TAIL")
    )
    private void afterRead(ReadView view, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof MobSpawnerBlockEntity) || !(self instanceof SpawnerIdHolder holder)) return;

        String s = view.getString("spawnertweaks:spawner_id", "");
        if (!s.isEmpty()) {
            try { holder.setSpawnerId(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }
        if (holder.getSpawnerId() == null) {
            holder.setSpawnerId(UUID.randomUUID());
        }
    }

    @Inject(
            method = "write(Lnet/minecraft/storage/WriteView;)V",
            at = @At("TAIL")
    )
    private void afterWrite(WriteView view, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof MobSpawnerBlockEntity) || !(self instanceof SpawnerIdHolder holder)) return;

        UUID id = holder.getSpawnerId();
        view.putString("spawnertweaks:spawner_id", id.toString());
    }
}
