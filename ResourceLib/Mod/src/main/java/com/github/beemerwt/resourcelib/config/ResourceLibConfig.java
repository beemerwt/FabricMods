package com.github.beemerwt.resourcelib.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @param resourcePackFile path to .zip
 * @param publicUrl        if not serving locally
 * @param sha1Hex          optional precomputed
 * @param dataFolder       where to store downloaded packs, e.g. resourcepacks/
 * @param bindAddress      HTTP bind address if serving locally (e.g., 0.0.0.0)
 * @param port             HTTP port if serving locally
 */
public record ResourceLibConfig(
        boolean serveLocally,
        Path resourcePackFile,
        String publicUrl,
        String sha1Hex,
        String dataFolder,
        String bindAddress,
        int port
) {
    private static final String DIR_NAME = "ResourceLib";
    private static final String FILE_NAME = "config.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Path.class, new PathAdapter())
            .create();

    public static ResourceLibConfig loadOrCreate() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(DIR_NAME);
        Path configFile = configDir.resolve(FILE_NAME);
        Path gameDir = FabricLoader.getInstance().getGameDir();

        ensureDir(configDir);

        if (!Files.exists(configFile)) {
            ResourceLibConfig def = defaultConfig(gameDir);
            write(configFile, def);
            return def;
        }

        // Overlay loaded JSON onto defaults so new fields auto-populate.
        ResourceLibConfig def = defaultConfig(gameDir);
        try (BufferedReader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            boolean serveLocally = getBool(obj, "serveLocally", def.serveLocally());
            Path resourcePackFile = getPath(obj, "resourcePackFile", def.resourcePackFile(), gameDir);
            String publicUrl = getString(obj, "publicUrl", def.publicUrl());
            String sha1Hex = getString(obj, "sha1Hex", def.sha1Hex());
            String dataFolder = getString(obj, "dataFolder", def.dataFolder());
            String bindAddress = getString(obj, "bindAddress", def.bindAddress());
            int port = getInt(obj, "port", def.port());

            ResourceLibConfig result = new ResourceLibConfig(
                    serveLocally,
                    resourcePackFile,
                    publicUrl,
                    sha1Hex,
                    dataFolder,
                    bindAddress,
                    port
            );

            // Persist merged result so users get any new keys.
            write(configFile, result);
            return result;
        } catch (Exception ex) {
            // Back up broken file and regenerate defaults.
            try {
                Path bak = configFile.resolveSibling(FILE_NAME + ".broken");
                Files.copy(configFile, bak);
            } catch (IOException ignored) {}
            ResourceLibConfig def2 = defaultConfig(gameDir);
            write(configFile, def2);
            return def2;
        }
    }

    private static void write(Path file, ResourceLibConfig cfg) {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            JsonObject o = new JsonObject();
            o.addProperty("serveLocally", cfg.serveLocally());
            o.addProperty("resourcePackFile", toRelOrAbs(cfg.resourcePackFile()));
            o.addProperty("publicUrl", cfg.publicUrl());
            o.addProperty("sha1Hex", cfg.sha1Hex() == null ? "" : cfg.sha1Hex());
            o.addProperty("dataFolder", cfg.dataFolder());
            o.addProperty("bindAddress", cfg.bindAddress());
            o.addProperty("port", cfg.port());
            GSON.toJson(o, w);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing " + file + ": " + e.getMessage(), e);
        }
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory: " + dir, e);
        }
    }

    public static ResourceLibConfig defaultConfig(Path gameDir) {
        // Default: serve locally on 0.0.0.0:8123, pack at <gameDir>/resourcepacks/server-pack.zip
        Path defaultPack = gameDir.resolve("resourcepacks").resolve("server-pack.zip");
        String url = "http://127.0.0.1:8123/server-pack.zip";
        String sha1Hex = ""; // leave blank to compute at runtime
        String dataFolder = "resourcepacks"; // relative to game dir
        String bind = "0.0.0.0";
        int port = 8123;

        return new ResourceLibConfig(
                true,
                defaultPack,
                url,
                sha1Hex,
                dataFolder,
                bind,
                port
        );
    }

    private static String toRelOrAbs(Path p) {
        Path gameDir = FabricLoader.getInstance().getGameDir().normalize();
        Path np = p.normalize();
        try {
            if (np.startsWith(gameDir)) {
                return gameDir.relativize(np).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {}
        return np.toString().replace('\\', '/');
    }

    private static boolean getBool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }

    private static int getInt(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }

    private static String getString(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static Path getPath(JsonObject o, String k, Path defAbs, Path gameDir) {
        if (!o.has(k)) return defAbs;
        String raw = o.get(k).getAsString();
        Path p = Path.of(raw);
        if (!p.isAbsolute()) p = gameDir.resolve(p);
        return p.normalize();
    }

    // Gson adapter for Path for completeness
    private static final class PathAdapter implements JsonSerializer<Path>, JsonDeserializer<Path> {
        @Override
        public JsonElement serialize(Path src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString().replace('\\', '/'));
        }
        @Override
        public Path deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Path.of(json.getAsString());
        }
    }
}
