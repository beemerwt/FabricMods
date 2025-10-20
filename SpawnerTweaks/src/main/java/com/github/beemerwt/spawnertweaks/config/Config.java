package com.github.beemerwt.spawnertweaks.config;

import java.util.HashMap;
import java.util.Map;

public class Config {

    /** Shared settings object. Any field -1 = keep vanilla. */
    public static class SpawnSettings {
        public int minSpawnDelay = 200;
        public int maxSpawnDelay = 800;
        public int spawnCount = 4;
        public int maxNearbyEntities = 6;
        public int requiredPlayerRange = 16;
        public int spawnRange = 4;
        public int spawnCap = 70;

        public SpawnSettings copy() {
            var s = new SpawnSettings();
            s.minSpawnDelay = this.minSpawnDelay;
            s.maxSpawnDelay = this.maxSpawnDelay;
            s.spawnCount = this.spawnCount;
            s.maxNearbyEntities = this.maxNearbyEntities;
            s.requiredPlayerRange = this.requiredPlayerRange;
            s.spawnRange = this.spawnRange;
            s.spawnCap = this.spawnCap;
            return s;
        }
    }

    /** Global defaults for all spawners. */
    public SpawnSettings defaults = new SpawnSettings();

    /** Per-entity overrides: "minecraft:zombie" -> { settings } */
    public Map<String, SpawnSettings> entityOverrides = new HashMap<>();

    /** Flags (JSON keys use dashes, see ConfigManager). */
    public boolean disable_safety_caps = false;
    public boolean disable_spawn_caps = false;
}
