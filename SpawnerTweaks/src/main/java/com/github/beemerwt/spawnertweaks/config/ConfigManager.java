package com.github.beemerwt.spawnertweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ConfigManager {

    /** For mapping dashed keys to fields. */
    private static final class ConfigSerde extends Config {
        @SerializedName("disable-safety-caps")
        Boolean disableSafetyCapsBoxed;
        @SerializedName("disable-spawn-caps")
        Boolean disableSpawnCapsBoxed;
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String CONFIG_DIR = "SpawnerTweaks";
    private static final String FILE_NAME = "config.json";
    private static volatile Config CURRENT;

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR).resolve(FILE_NAME);
    }

    public static synchronized void load() {
        var p = path();
        try {
            if (!Files.exists(p)) {
                ensureParent(p);
                CURRENT = defaultConfig();
                save();
                return;
            }

            try (Reader r = Files.newBufferedReader(p)) {
                ConfigSerde serde = GSON.fromJson(r, ConfigSerde.class);
                if (serde == null) serde = new ConfigSerde();

                if (serde.disableSafetyCapsBoxed != null)
                    serde.disable_safety_caps = serde.disableSafetyCapsBoxed;
                if (serde.disableSpawnCapsBoxed != null)
                    serde.disable_spawn_caps = serde.disableSpawnCapsBoxed;

                CURRENT = serde;
                if (CURRENT.defaults == null) CURRENT.defaults = new Config.SpawnSettings();
                if (CURRENT.entityOverrides == null) CURRENT.entityOverrides = java.util.Map.of();
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (CURRENT == null) CURRENT = defaultConfig();
        }
    }

    public static synchronized void save() {
        var p = path();
        try {
            ensureParent(p);
            try (Writer w = Files.newBufferedWriter(p)) {
                var json = emitWithDashedKeys(CURRENT);
                w.write(json);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Config get() {
        if (CURRENT == null) load();
        return CURRENT;
    }

    // ----- Resolution -----

    public static Config.SpawnSettings effectiveSettings(EntityType<?> type) {
        var cfg = get();
        var base = cfg.defaults.copy();
        var id = Registries.ENTITY_TYPE.getId(type);
        var key = id.toString();
        var over = cfg.entityOverrides.get(key);
        if (over != null) mergeInto(base, over);

        if (!cfg.disable_safety_caps) clamp(base);
        if (cfg.disable_spawn_caps) base.spawnCap = -1;

        return base;
    }

    // ----- Utility -----

    private static void mergeInto(Config.SpawnSettings base, Config.SpawnSettings override) {
        if (override.minSpawnDelay != -1) base.minSpawnDelay = override.minSpawnDelay;
        if (override.maxSpawnDelay != -1) base.maxSpawnDelay = override.maxSpawnDelay;
        if (override.spawnCount != -1) base.spawnCount = override.spawnCount;
        if (override.maxNearbyEntities != -1) base.maxNearbyEntities = override.maxNearbyEntities;
        if (override.requiredPlayerRange != -1) base.requiredPlayerRange = override.requiredPlayerRange;
        if (override.spawnRange != -1) base.spawnRange = override.spawnRange;
        if (override.spawnCap != -1) base.spawnCap = override.spawnCap;
    }

    private static void clamp(Config.SpawnSettings s) {
        s.minSpawnDelay = clampInt(s.minSpawnDelay, 1, 120000);
        s.maxSpawnDelay = clampInt(s.maxSpawnDelay, 1, 240000);
        s.spawnCount = clampInt(s.spawnCount, 1, 64);
        s.maxNearbyEntities = clampInt(s.maxNearbyEntities, 0, 128);
        s.requiredPlayerRange = clampInt(s.requiredPlayerRange, 1, 128);
        s.spawnRange = clampInt(s.spawnRange, 1, 32);
        s.spawnCap = clampInt(s.spawnCap, 1, 1000);
        if (s.minSpawnDelay > s.maxSpawnDelay) {
            int tmp = s.minSpawnDelay;
            s.minSpawnDelay = s.maxSpawnDelay;
            s.maxSpawnDelay = tmp;
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return (v == -1) ? v : Math.max(lo, Math.min(hi, v));
    }

    private static void ensureParent(Path p) throws IOException {
        var parent = p.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private static Config defaultConfig() {
        var c = new Config();
        c.defaults = new Config.SpawnSettings();
        c.entityOverrides.put("minecraft:cave_spider", new Config.SpawnSettings() {{
            minSpawnDelay = 100;
            maxSpawnDelay = 400;
            spawnCount = 2;
            spawnCap = 45;
        }});
        c.disable_safety_caps = false;
        c.disable_spawn_caps = false;
        return c;
    }

    private static String emitWithDashedKeys(Config cfg) {
        String json = GSON.toJson(cfg);
        json = json.replace("\"disable_safety_caps\":", "\"disable-safety-caps\":");
        json = json.replace("\"disable_spawn_caps\":", "\"disable-spawn-caps\":");
        return json;
    }
}
