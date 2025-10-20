package com.github.beemerwt.fakeplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FakePlayerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "fakeplayer.json";

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static FakePlayerConfig load() {
        Path p = configPath();
        if (!Files.exists(p)) return new FakePlayerConfig();
        try (BufferedReader br = Files.newBufferedReader(p)) {
            FakePlayerConfig cfg = GSON.fromJson(br, FakePlayerConfig.class);
            return (cfg != null) ? cfg : new FakePlayerConfig();
        } catch (IOException e) {
            e.printStackTrace();
            return new FakePlayerConfig();
        }
    }

    public static void save(FakePlayerConfig cfg) {
        Path p = configPath();
        try {
            Files.createDirectories(p.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(p)) {
                GSON.toJson(cfg, bw);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Optional<RegistryKey<World>> parseDim(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return Optional.empty();
        return Optional.of(RegistryKey.of(RegistryKeys.WORLD, ident));
    }

    public static String dimToString(RegistryKey<World> key) {
        return key.getValue().toString();
    }
}

