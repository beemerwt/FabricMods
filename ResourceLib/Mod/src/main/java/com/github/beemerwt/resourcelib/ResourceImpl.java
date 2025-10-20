package com.github.beemerwt.resourcelib;

import com.github.beemerwt.resourcelib.config.ResourceLibConfig;
import com.github.beemerwt.resourcelib.http.EmbeddedHttpServer;
import com.github.beemerwt.resourcelib.io.LoadOrderManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ResourceImpl implements ResourceApi {
    private final ResourceLibConfig config;
    private EmbeddedHttpServer http;

    private final List<File> javaRegistered = new ArrayList<>();
    private final List<File> bedrockRegistered = new ArrayList<>();
    private List<File> javaOrdered = List.of();
    private List<File> bedrockOrdered = List.of();

    ResourceImpl(ResourceLibConfig cfg) {
        this.config = cfg;
    }

    synchronized void start() throws Exception {
        // Load/merge persisted load order
        Path loadOrderPath = FabricLoader.getInstance().getConfigDir()
                .resolve("ResourceLib").resolve("loadorder.yml");
        LoadOrderManager lom = new LoadOrderManager(loadOrderPath);
        javaOrdered = lom.resolveOrder("java", javaRegistered);
        bedrockOrdered = lom.resolveOrder("bedrock", bedrockRegistered);

        // Spin up local HTTP if needed
        if (config.serveLocally()) {
            http = new EmbeddedHttpServer(config);
            http.start();
            for (File f : javaOrdered) http.register(f.toPath());
            for (File f : bedrockOrdered) http.register(f.toPath());
        }

        ResourceLib.LOGGER.info("ResourceImpl ready. Java packs: {}, Bedrock packs: {}",
                javaOrdered.size(), bedrockOrdered.size());
    }

    synchronized void stop() {
        if (http != null) {
            http.stop();
            http = null;
        }
    }

    public List<File> getJavaPacks() { return List.copyOf(javaOrdered); }
    public List<File> getBedrockPacks() { return List.copyOf(bedrockOrdered); }

    public Optional<String> urlFor(File f) {
        if (config.serveLocally()) {
            return Optional.ofNullable(http).map(h -> h.publicUrlFor(f.getName()));
        }
        // For remote mode, config.publicUrl() is treated as a direct URL to a single pack.
        // If multiple packs are configured, extend config to support a map; for now return it as-is.
        return Optional.ofNullable(config.publicUrl());
    }

    public static void sendJavaPack(ServerPlayerEntity player, String url, String sha1Hex, boolean required, String prompt) {
        // Packet signature (1.21.x): (UUID id, String url, String hashHex, boolean required, Optional<Text> prompt)
        ResourcePackSendS2CPacket pkt = new ResourcePackSendS2CPacket(
                UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)),
                url,
                sha1Hex,
                required,
                Optional.of(Text.literal(prompt))
        );
        player.networkHandler.sendPacket(pkt);
    }

    public void pushBedrockPacks(SessionLike eventLike) {
        // Register for this session using Geyser API (url preferred; falls back to local file path).
        for (File f : getBedrockPacks()) {
            Optional<String> maybeUrl = urlFor(f);
            ResourcePack pack;
            pack = maybeUrl.map(s -> ResourcePack.create(PackCodec.url(s)))
                    .orElseGet(() -> ResourcePack.create(PackCodec.path(f.toPath())));
            eventLike.register(pack);
        }
    }

    @Override
    public Path getPackDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(config.dataFolder());
    }

    @Override
    public void addBedrockPack(File file) {
        // Validate that the file exists
        if (!file.exists() || !file.isFile()) {
            ResourceLib.LOGGER.warn("Bedrock pack file does not exist or is not a file: {}", file.getAbsoluteFile());
            return;
        }

        bedrockRegistered.add(file);
        ResourceLib.LOGGER.info("Added Bedrock pack: {}", file.getAbsoluteFile());
    }

    @Override
    public void addJavaPack(File file) {
        // Validate that the file exists
        if (!file.exists() || !file.isFile()) {
            ResourceLib.LOGGER.warn("Java pack file does not exist or is not a file: {}", file.getAbsoluteFile());
            return;
        }

        javaRegistered.add(file);
        ResourceLib.LOGGER.info("Added Java pack: {}", file.getAbsoluteFile());
    }

    /** Minimal bridge so we can unit-test the per-session registration without Geyser present. */
    interface SessionLike {
        void register(@NotNull ResourcePack pack, @Nullable ResourcePackOption<?>... options);
    }
}
