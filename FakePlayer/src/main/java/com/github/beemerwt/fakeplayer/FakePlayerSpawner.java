package com.github.beemerwt.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class FakePlayerSpawner {
    private FakePlayerSpawner() {}

    public static ServerPlayerEntity createAndJoin(MinecraftServer server,
                                                   NullClientConnection connection,
                                                   GameProfile profile,
                                                   ServerWorld world,
                                                   BlockPos pos,
                                                   float yaw,
                                                   float pitch) {
        PlayerManager pm = server.getPlayerManager();
        SyncedClientOptions opts = SyncedClientOptions.createDefault();

        // Create the player entity. Signatures vary slightly across 1.21.x; this variant is valid on 1.21.8/9 Yarn.
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, opts);

        // Construct the network handler and bind it to our null connection.
        ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
        ServerPlayNetworkHandler handler = new ServerPlayNetworkHandler(server, connection, player, clientData);
        connection.setInitialPacketListener(handler);
        player.networkHandler = handler;

        // Finish the normal join pathway so scoreboards, adv, teams, bossbars, permissions, etc. are consistent.
        pm.onPlayerConnect(connection, player, clientData);

        // Position the player.
        player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
        return player;
    }
}
