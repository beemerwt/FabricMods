package com.github.beemerwt.fakeplayer;

import com.github.beemerwt.fakeplayer.service.VisibilityService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FakePlayer implements ModInitializer {
    public static final String TEAM_NAME = "fakeplayer";
    private static final Logger LOGGER = LoggerFactory.getLogger("FakePlayer");

    public static Logger getLogger() {
        return LOGGER;
    }

    @Override
    public void onInitialize() {
        FakePlayerRegistry.init();
        PreBootTickets.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            FakePlayerCommands.register(dispatcher);
        });

        ServerPlayerEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(this::onPlayerLeave);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onPlayerJoin(ServerPlayerEntity player) {
        LOGGER.debug("Player joined: {}", player.getStringifiedName());
        VisibilityService.onViewerJoin(player, FakePlayerRegistry.list());
    }

    private void onPlayerLeave(ServerPlayerEntity player) {
        LOGGER.debug("Player left: {}", player.getStringifiedName());
        var sb = player.getEntityWorld().getServer().getScoreboard();
        var team = sb.getScoreHolderTeam(player.getNameForScoreboard());
        if (team != null && team.getName().equalsIgnoreCase(TEAM_NAME)) {
            sb.removeScoreHolderFromTeam(player.getNameForScoreboard(), team);
        }
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.debug("Server started: auto-spawning fake players as configured.");
        // Remake the fakeplayer team
        var sb = server.getScoreboard();
        var existingTeam = sb.getTeam(TEAM_NAME);
        if (existingTeam != null) sb.removeTeam(existingTeam);
        FakePlayerSpawner.ensureNoEntityCollisionTeam(sb);

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

        PreBootTickets.clearAll(server);
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.debug("Server stopping: cleaning up fake players.");
        var pm = server.getPlayerManager();
        var sb = server.getScoreboard();

        // Remove all real players from the fakeplayer team if they are in it
        for (var player : pm.getPlayerList()) {
            if (FakePlayerRegistry.getByName(player.getStringifiedName()).isPresent()) continue;
            var team = sb.getScoreHolderTeam(player.getNameForScoreboard());
            if (team != null && team.getName().equalsIgnoreCase(TEAM_NAME)) {
                sb.removeScoreHolderFromTeam(player.getNameForScoreboard(), team);
            }
        }

        // Cleanly disconnect all active fake players
        FakePlayerRegistry.list().forEach(p -> {
            try {
                p.networkHandler.disconnect(Text.literal("Server stopping"));
            } catch (Throwable ignored) {}
        });
    }
}
