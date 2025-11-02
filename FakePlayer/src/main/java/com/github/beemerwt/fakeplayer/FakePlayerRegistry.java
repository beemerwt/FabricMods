package com.github.beemerwt.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerRegistry {
    // Keep light state so we can clean up reliably.
    private static final Map<UUID, ServerPlayerEntity> playersById = new ConcurrentHashMap<>();
    private static final Map<String, UUID> nameToId = new ConcurrentHashMap<>();
    private static FakePlayerConfig config = new FakePlayerConfig();

    private FakePlayerRegistry() {}

    public static void init() {
        config = FakePlayerConfigManager.load();

        ServerTickEvents.START_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (!p.getCommandTags().contains("fakeplayer")) continue;

                // Keep invisibility up
                if (!p.isInvisible()) p.setInvisible(true);
                if (!p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    p.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.INVISIBILITY, 20 * 60 * 5, 0, true, false, false));
                }
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (!p.getCommandTags().contains("fakeplayer")) continue;

                // if any of these are on, skip gravity (matches vanilla)
                if (p.hasNoGravity() || p.noClip || p.getAbilities().flying) continue;

                // apply gravity + drag and move
                double g = p.getFinalGravity();               // uses vanilla gravity (accounts for effects)
                var v = p.getVelocity().add(0.0, -g, 0.0);

                // vanilla-ish drag; on ground Y drag is handled after collisions, keep it simple:
                v = new Vec3d(v.x * 0.98, v.y * 0.98, v.z * 0.98);

                p.setVelocity(v);                        // marks velocity dirty for clients
                p.move(MovementType.SELF, v);            // resolves collisions & sets onGround
            }
        });
    }

    public static FakePlayerConfig getConfig() {
        return config;
    }

    public static void saveConfig() {
        FakePlayerConfigManager.save(config);
    }

    public static Collection<ServerPlayerEntity> list() {
        return Collections.unmodifiableCollection(playersById.values());
    }

    public static Optional<ServerPlayerEntity> getByName(String name) {
        UUID id = nameToId.get(name.toLowerCase(Locale.ROOT));
        if (id == null) return Optional.empty();
        return Optional.ofNullable(playersById.get(id));
    }

    public static boolean exists(String name) {
        return getByName(name).isPresent();
    }

    public static ServerPlayerEntity spawn(MinecraftServer server, String name, ServerWorld world,
                                           BlockPos pos, float yaw, float pitch) {
        if (exists(name)) throw new IllegalStateException("Fake player '" + name + "' already exists");

        UUID uuid = uuidFromName(name);
        GameProfile profile = new GameProfile(uuid, name);

        var conn = new NullClientConnection();
        var player = FakePlayerSpawner.createAndJoin(server, conn, profile, world, pos, yaw, pitch);

        playersById.put(uuid, player);
        nameToId.put(name.toLowerCase(Locale.ROOT), uuid);

        // upsert into config (autoSpawn=true by default)
        String dim = FakePlayerConfigManager.dimToString(world.getRegistryKey());
        upsertConfigEntry(name, dim, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
        saveConfig();
        return player;
    }

    public static boolean remove(MinecraftServer server, String name) {
        Optional<ServerPlayerEntity> opt = getByName(name);
        if (opt.isEmpty()) return false;

        ServerPlayerEntity p = opt.get();
        p.networkHandler.disconnect(Text.literal("Removed by server"));
        playersById.remove(p.getUuid());
        nameToId.remove(p.getGameProfile().name().toLowerCase(Locale.ROOT));

        // Despawn immediately
        server.getPlayerManager().remove(p);

        // remove from config
        config.players.removeIf(e -> e.name.equalsIgnoreCase(name));
        saveConfig();
        return true;
    }

    private static void upsertConfigEntry(String name, String dim, double x, double y, double z,
                                          float yaw, float pitch) {
        for (var e : config.players) {
            if (e.name.equalsIgnoreCase(name)) {
                e.dimension = dim; e.x = x; e.y = y; e.z = z; e.yaw = yaw; e.pitch = pitch; e.autoSpawn = true;
                return;
            }
        }
        config.players.add(new FakePlayerConfig.Entry(name, dim, x, y, z, yaw, pitch, true));
    }

    private static UUID uuidFromName(String name) {
        // Vanilla offline UUID algorithm: UUID.nameUUIDFromBytes("OfflinePlayer:" + name)
        String seed = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
