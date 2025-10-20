package com.github.beemerwt.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

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
        FakeVanish.hideForAll(server, player);
        ghostify(player);

        playersById.put(uuid, player);
        nameToId.put(name.toLowerCase(Locale.ROOT), uuid);

        // upsert into config (autoSpawn=true by default)
        String dim = FakePlayerConfigManager.dimToString(world.getRegistryKey());
        upsertConfigEntry(name, dim, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch, true);
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

    private static void ghostify(ServerPlayerEntity p) {
        p.setInvisible(true);
        p.setSilent(true);
        p.setInvulnerable(true);
        p.noClip = true;                    // no collisions
        p.setNoGravity(true);               // no gravity
        p.setVelocity(0, 0, 0);    // static
        p.setSprinting(false);
        p.setSneaking(false);

        // Prevent accidental movement updates/sounds
        p.setOnGround(true);
        p.fallDistance = 0.0f;

        // Scoreboard team: kill ALL collisions server-side
        var sb = p.getEntityWorld().getServer().getScoreboard();
        var teamName = "fakeplayer:ghost";
        var team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.addTeam(teamName);
            team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
            team.setFriendlyFireAllowed(false); // irrelevant, but harmless
            team.setShowFriendlyInvisibles(false);
        }

        // Preferred on newer Yarn:
        try {
            // 1.21.x API – adds and broadcasts correctly
            sb.addScoreHolderToTeam(p.getUuidAsString(), team);
        } catch (NoSuchMethodError ignore) {
            // Fallback: manipulate the member set (works if method isn’t present)
            team.getPlayerList().add(p.getUuidAsString());
        }
    }

    private static void upsertConfigEntry(String name, String dim, double x, double y, double z,
                                          float yaw, float pitch, boolean autoSpawn) {
        for (var e : config.players) {
            if (e.name.equalsIgnoreCase(name)) {
                e.dimension = dim; e.x = x; e.y = y; e.z = z; e.yaw = yaw; e.pitch = pitch; e.autoSpawn = autoSpawn;
                return;
            }
        }
        config.players.add(new FakePlayerConfig.Entry(name, dim, x, y, z, yaw, pitch, autoSpawn));
    }

    private static UUID uuidFromName(String name) {
        // Vanilla offline UUID algorithm: UUID.nameUUIDFromBytes("OfflinePlayer:" + name)
        String seed = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
