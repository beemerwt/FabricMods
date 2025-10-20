package com.github.beemerwt.wrench;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class WrenchConfig {
    private static final Logger LOG = LoggerFactory.getLogger("Wrench/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Path DIR  = FabricLoader.getInstance().getConfigDir().resolve("Wrench");
    public static final Path FILE = DIR.resolve("config.json");

    @SerializedName("cooldown_ticks")
    public int cooldownTicks = 4;

    @SerializedName("require_sneak")
    public boolean requireSneak = false;

    public List<String> machines = defaultMachines();

    // runtime cache
    private transient Set<Identifier> machineIds = Set.of();

    public static WrenchConfig load() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(DIR);
                WrenchConfig cfg = new WrenchConfig();
                cfg.rebuildCache();
                try (Writer w = new OutputStreamWriter(Files.newOutputStream(FILE), StandardCharsets.UTF_8)) {
                    GSON.toJson(cfg, w);
                }
                LOG.info("[Wrench] Wrote default config at {}", FILE);
                return cfg;
            }
            try (Reader r = new InputStreamReader(Files.newInputStream(FILE), StandardCharsets.UTF_8)) {
                WrenchConfig cfg = GSON.fromJson(r, WrenchConfig.class);
                cfg.rebuildCache();
                LOG.info("[Wrench] Loaded config ({} machines, cooldown {}t, require_sneak={})",
                        cfg.machineIds.size(), cfg.cooldownTicks, cfg.requireSneak);
                return cfg;
            }
        } catch (Exception e) {
            LOG.error("[Wrench] Failed to load config, falling back to defaults", e);
            WrenchConfig cfg = new WrenchConfig();
            cfg.rebuildCache();
            return cfg;
        }
    }

    public boolean isMachine(Identifier blockId) {
        return machineIds.contains(blockId);
    }

    private void rebuildCache() {
        Set<Identifier> out = new HashSet<>();
        if (machines != null) {
            for (String raw : machines) {
                if (raw == null) continue;
                String s = raw.trim();
                if (s.isEmpty()) continue;
                // Accept "minecraft:hopper" or "HOPPER" etc.
                String norm = s.contains(":") ? s.toLowerCase(Locale.ROOT)
                        : "minecraft:" + s.toLowerCase(Locale.ROOT);
                Identifier id = Identifier.tryParse(norm);
                if (id == null) {
                    LOG.warn("[Wrench] Bad block id '{}'", s);
                    continue;
                }
                if (!Registries.BLOCK.containsId(id)) {
                    LOG.warn("[Wrench] Unknown block id '{}' (not in registry)", id);
                }
                out.add(id);
            }
        }
        this.machineIds = Collections.unmodifiableSet(out);
    }

    private static List<String> defaultMachines() {
        return List.of(
                "minecraft:hopper","minecraft:dropper","minecraft:dispenser","minecraft:observer",
                "minecraft:piston","minecraft:sticky_piston",
                "minecraft:furnace","minecraft:blast_furnace","minecraft:smoker",
                "minecraft:comparator","minecraft:repeater","minecraft:redstone_torch","minecraft:lever",
                "minecraft:stonecutter","minecraft:grindstone","minecraft:loom","minecraft:smithing_table",
                "minecraft:cartography_table","minecraft:barrel","minecraft:brewing_stand","minecraft:composter",
                "minecraft:note_block","minecraft:lectern","minecraft:lightning_rod","minecraft:sculk_catalyst"
        );
    }
}

