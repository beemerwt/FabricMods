package com.github.beemerwt.fakeplayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class FakePlayer implements ModInitializer {
    public static final String MODID = "fakeplayer";

    @Override
    public void onInitialize() {
        FakePlayerRegistry.init();
        FakeVanish.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            FakePlayerCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarted(MinecraftServer server) {
        var cfg = FakePlayerRegistry.getConfig();
        int spawned = 0;
        for (var e : cfg.players) {
            if (!e.autoSpawn) continue;
            var dimKeyOpt = FakePlayerConfigManager.parseDim(e.dimension);
            if (dimKeyOpt.isEmpty()) continue;
            var world = server.getWorld(dimKeyOpt.get());
            if (world == null) continue;

            try {
                var pos = BlockPos.ofFloored(e.x, e.y, e.z);
                FakePlayerRegistry.spawn(server, e.name, world, pos, e.yaw, e.pitch);
                spawned++;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        if (spawned > 0) {
            server.getPlayerManager().broadcast(Text.literal("[FakePlayer] Auto-spawned "
                    + spawned + " fake player(s)."), false);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        // Cleanly disconnect all active fake players
        FakePlayerRegistry.list().forEach(p -> {
            try {
                p.networkHandler.disconnect(Text.literal("Server stopping"));
            } catch (Throwable ignored) {}
        });
    }
}
