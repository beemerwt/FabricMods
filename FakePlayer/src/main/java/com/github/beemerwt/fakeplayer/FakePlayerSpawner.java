package com.github.beemerwt.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

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
        player.addCommandTag("fakeplayer");

        // Construct the network handler and bind it to our null connection.
        ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
        ServerPlayNetworkHandler handler = new ServerPlayNetworkHandler(server, connection, player, clientData);
        connection.setInitialPacketListener(handler);
        player.networkHandler = handler;

        // Finish the normal join pathway so scoreboards, adv, teams, bossbars, permissions, etc. are consistent.
        pm.onPlayerConnect(connection, player, clientData);

        player.changeGameMode(GameMode.SURVIVAL);
        player.setClientOptions(SyncedClientOptions.createDefault());

        ghostify(player);

        // Position the player.
        player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
        world.getChunkManager().updatePosition(player);
        return player;
    }

    static void ghostify(ServerPlayerEntity p) {
        p.getAbilities().allowFlying = false;
        p.getAbilities().flying = false;
        p.getAbilities().invulnerable = false; // optional, but avoids weirdness
        p.sendAbilitiesUpdate();

        p.setSilent(true);
        p.setInvulnerable(true);
        p.setSprinting(false);
        p.setSneaking(false);

        p.setNoGravity(false);
        p.noClip = false;

        p.setInvisible(true);

        var sb = p.getEntityWorld().getServer().getScoreboard();
        var team = ensureNoEntityCollisionTeam(sb);
        sb.addScoreHolderToTeam(p.getNameForScoreboard(), team);
    }

    static Team ensureNoEntityCollisionTeam(Scoreboard sb) {
        // Scoreboard team: kill ALL collisions server-side
        var team = sb.getTeam(FakePlayer.TEAM_NAME);
        if (team == null) {
            team = sb.addTeam(FakePlayer.TEAM_NAME);
            team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.HIDE_FOR_OTHER_TEAMS);
            team.setFriendlyFireAllowed(false); // irrelevant, but harmless
            team.setShowFriendlyInvisibles(true);
        }
        return team;
    }
}
