package com.github.beemerwt.spawnertweaks.mixin;

import java.util.UUID;

public interface SpawnerIdHolder {
    UUID getSpawnerId();
    void setSpawnerId(UUID id);
}

