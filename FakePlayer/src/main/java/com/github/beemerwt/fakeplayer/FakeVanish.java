package com.github.beemerwt.fakeplayer;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class FakeVanish {
    private FakeVanish() {}

    public static void init() {
        // When a real player joins, hide all fake ones from that newcomer.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var joiner = handler.player;
            for (var fp : FakePlayerRegistry.list()) {
                hideFor(joiner, fp);
            }
        });

        ServerPlayerEvents.JOIN.register((player) -> {
            for (var fp : FakePlayerRegistry.list())
                hideFor(player, fp);
        });

        // Optional: on server started, make sure any already-spawned fakes are hidden from all.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (var fp : FakePlayerRegistry.list()) hideForAll(server, fp);
        });
    }

    public static void hideForAll(MinecraftServer server, ServerPlayerEntity fake) {
        for (var viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer == fake) continue;
            hideFor(viewer, fake);
        }
    }

    private static void hideFor(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        // 1) remove from viewer’s tab list
        viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(fake.getUuid())));

        // 2) destroy fake’s entity on viewer
        viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(fake.getId()));
    }
}
