package com.github.beemerwt.fakeplayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.*;

public final class PreBootTickets {
    private static final Map<String, Set<ChunkPos>> ADDED = new HashMap<>();

    private PreBootTickets() {}

    public static void register() {
        // Phase 1: BEFORE first world tick — add radius FORCED tickets around each autospawn fake player.
        ServerWorldEvents.LOAD.register((server, world) -> {
            var cfg = FakePlayerRegistry.getConfig();
            var entries = entriesForWorld(cfg, world.getRegistryKey());
            if (entries.isEmpty()) return;

            int simDist = resolveSimDist(server);
            ServerChunkManager cm = world.getChunkManager();

            for (var e : entries) {
                ChunkPos center = new ChunkPos(ofPlayer(e));
                // Radius ticket centered on the player's chunk; chunk system expands it internally.
                cm.addTicket(ChunkTicketType.FORCED, center, simDist);
            }
        });
    }

    private static int resolveSimDist(MinecraftServer server) {
        int d = server.getPlayerManager().getSimulationDistance();
        return d > 0 ? d : 10; // default fallback
    }

    private static BlockPos ofPlayer(FakePlayerConfig.Entry player) {
        return BlockPos.ofFloored(player.x, player.y, player.z);
    }

    private static List<FakePlayerConfig.Entry> entriesForWorld(FakePlayerConfig cfg, RegistryKey<World> worldKey) {
        var out = new ArrayList<FakePlayerConfig.Entry>();
        for (var e : cfg.players) {
            if (!e.autoSpawn) continue;
            var dimKeyOpt = FakePlayerConfigManager.parseDim(e.dimension);
            if (dimKeyOpt.isEmpty()) continue;
            if (!Objects.equals(dimKeyOpt.get(), worldKey)) continue;
            out.add(e);
        }
        return out;
    }

    public static void clearAll(MinecraftServer server) {
        server.getWorlds().forEach(world -> {
            var cm = world.getChunkManager();
            var key = world.getRegistryKey().getValue().toString();
            var set = ADDED.remove(key);

            for (var t : FakePlayerRegistry.getConfig().players) {
                var dimKeyOpt = FakePlayerConfigManager.parseDim(t.dimension);
                if (dimKeyOpt.isEmpty()) continue;
                if (!dimKeyOpt.get().equals(world.getRegistryKey())) continue;

                var cp = new ChunkPos(ofPlayer(t));
                if (set != null) {
                    cm.removeTicket(ChunkTicketType.FORCED, cp, server.getPlayerManager().getSimulationDistance());
                }
            }
        });
    }
}

