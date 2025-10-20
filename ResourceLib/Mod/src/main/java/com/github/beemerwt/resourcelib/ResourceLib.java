package com.github.beemerwt.resourcelib;

import com.github.beemerwt.resourcelib.config.ResourceLibConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;

public final class ResourceLib implements ModInitializer, EventRegistrar {
    private static volatile MinecraftServer server;
    private static volatile ResourceImpl impl;

    private ResourceLibConfig cfg;

    public static final Logger LOGGER = LoggerFactory.getLogger("ResourceLib");

    public static MinecraftServer getServer() { return server; }
    public static ResourceImpl implementation() { return impl; }

    @Override
    public void onInitialize() {
        cfg = ResourceLibConfig.loadOrCreate();
        impl = new ResourceImpl(cfg);
        ResourceApis.install(impl);
        LOGGER.info("[ResourceLib] initializing.");

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            try {
                impl.start();
                LOGGER.info("[ResourceLib] Internal server started.");
            } catch (Exception ex) {
                LOGGER.error("[ResourceLib] Failed to start ResourceLib", ex);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            try { if (impl != null) impl.stop(); } catch (Exception ignored) {}
        });

        // Push Java packs on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            List<File> javaPacks = impl.getJavaPacks();
            for (File f : javaPacks) {
                Optional<String> url = impl.urlFor(f);
                if (url.isEmpty()) {
                    LOGGER.warn("No URL available for {} (publicUrl not set and server not serving locally).", f.getName());
                    continue;
                }
                String sha1 = (cfg.sha1Hex() != null && !cfg.sha1Hex().isBlank())
                        ? cfg.sha1Hex()
                        : com.github.beemerwt.resourcelib.util.Hashes.sha1Hex(f.toPath());

                ServerPlayerEntity p = handler.player;
                ResourceImpl.sendJavaPack(p, url.get(), sha1, true, "Server resource pack required");
            }
        });

        // Bedrock (Geyser) per-session pack registration
        try {
            GeyserApi.api().eventBus().subscribe(this, SessionLoadResourcePacksEvent.class,
                    this::onSessionLoadResourcePack);
            LOGGER.info("Registered Geyser SessionLoadResourcePacksEvent handler.");
        } catch (Throwable t) {
            // Geyser not present; that's fine.
            LOGGER.info("Geyser not detected; Bedrock pack registration disabled.");
        }
    }

    @Subscribe
    private void onSessionLoadResourcePack(SessionLoadResourcePacksEvent event) {
        impl.pushBedrockPacks(event::register);
    }


}
