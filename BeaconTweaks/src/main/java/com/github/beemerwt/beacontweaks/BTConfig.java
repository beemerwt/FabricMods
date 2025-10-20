package com.github.beemerwt.beacontweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BTConfig {
    public int tickInterval = 80;
    public boolean boostAmplifier = false; // If true, set amplifier = Math.max(0, level-1); else vanilla-ish (0 or 1 at L4).
    public boolean includeSecondary = true;

    // If >= 0, all beacons use this radius (ignores level constants). -1 disables.
    public int forceAllBeaconsRange = -1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/beacontweaks.json";

    public static BTConfig loadOrCreate() {
        Path p = Path.of(FILE);
        try {
            if (Files.exists(p)) {
                try (var r = Files.newBufferedReader(p)) {
                    var cfg = GSON.fromJson(r, BTConfig.class);
                    return (cfg != null) ? cfg : new BTConfig();
                }
            } else {
                var def = new BTConfig();
                save(def);
                return def;
            }
        } catch (IOException e) {
            BeaconTweaks.LOGGER.warn("Failed to load config, using defaults", e);
            return new BTConfig();
        }
    }

    public static void save(BTConfig cfg) {
        Path p = Path.of(FILE);
        try {
            Files.createDirectories(p.getParent());
            try (var w = Files.newBufferedWriter(p)) {
                GSON.toJson(cfg, w);
            }
        } catch (IOException e) {
            BeaconTweaks.LOGGER.warn("Failed to save config", e);
        }
    }
}
