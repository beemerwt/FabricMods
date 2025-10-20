package com.github.beemerwt.essence.util;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.StoredLocation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Objects;

public final class Locations {
    public static StoredLocation capture(ServerPlayerEntity p) {
        var worldKey = p.getEntityWorld().getRegistryKey().getValue().toString();
        return new StoredLocation(
                worldKey,
                p.getX(), p.getY(), p.getZ(),
                p.getYaw(), p.getPitch()
        );
    }

    private Locations() {}

    public static RegistryKey<World> toRegistryKey(StoredLocation loc) {
        Objects.requireNonNull(loc, "loc");
        Identifier id = Identifier.of(loc.worldKey());
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }

    /** String form ("minecraft:overworld" etc.) from a RegistryKey. */
    public static String fromRegistryKey(RegistryKey<World> key) {
        return key.getValue().toString();
    }

    /** Lookup the ServerWorld for a StoredLocation on this server (null if missing/unloaded). */
    public static ServerWorld resolveWorld(StoredLocation loc) {
        return Essence.getServer().getWorld(toRegistryKey(loc));
    }

    public static RegistryKey<World> worldKeyFromString(String keyString) {
        // Example input: "minecraft:overworld" or "the_nether"
        Identifier id = Identifier.tryParse(keyString);
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }

    public static ServerWorld resolveWorld(String worldString) {
        var worldKey = worldKeyFromString(worldString);
        var server = Essence.getServer();
        return server.getWorld(worldKey);
    }


    public static StoredLocation fromWorldWithFacing(ServerWorld world, BlockPos pos, Direction facing) {
        var quat = facing.getRotationQuaternion();
        Vector3f euler = new Vector3f();
        quat.getEulerAnglesXYZ(euler);
        var angles = new Vec2f((float) Math.toDegrees(euler.y()), (float) Math.toDegrees(euler.x()));
        return fromWorld(world, pos, angles);
    }

    public static StoredLocation fromWorld(ServerWorld world, BlockPos pos, Vec2f facing) {
        return new StoredLocation(
                world.getRegistryKey().getValue().toString(),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                facing.x, facing.y
        );
    }

    public static StoredLocation fromWorld(ServerWorld world, BlockPos pos) {
        return fromWorldWithFacing(world, pos, Direction.NORTH);
    }

    public static @NotNull StoredLocation fromSpawnPoint(WorldProperties.SpawnPoint spawnPoint) {
        return fromSpawnPoint(spawnPoint, Vec3d.ZERO);
    }

    public static @NotNull StoredLocation fromSpawnPoint(WorldProperties.SpawnPoint spawnPoint, Vec3d offset) {
        var pos = spawnPoint.getPos();
        var world = spawnPoint.getDimension();
        return new StoredLocation(
                world.getValue().toString(),
                pos.getX() + offset.x,
                pos.getY() + offset.y,
                pos.getZ() + offset.z,
                spawnPoint.yaw(),
                spawnPoint.pitch()
        );
    }
}
