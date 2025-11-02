package com.github.beemerwt.essence.core.util;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.model.StoredLocation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public final class Locations {
    private Locations() { }

    public static StoredLocation capture(ServerPlayerEntity p) {
        return new StoredLocation(
            p.getEntityWorld().getRegistryKey(),
            p.getX(), p.getY(), p.getZ(),
            p.getYaw(), p.getPitch()
        );
    }

    /**
     * Lookup the ServerWorld for a StoredLocation on this server (null if missing/unloaded).
     */
    public static ServerWorld resolveWorld(StoredLocation loc) {
        return Essence.getServer().getWorld(loc.worldKey());
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
            world.getRegistryKey(),
            pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
            facing.x, facing.y
        );
    }

    public static StoredLocation fromWorld(ServerWorld world, BlockPos pos) {
        return fromWorldWithFacing(world, pos, Direction.NORTH);
    }

    public static StoredLocation fromWorld(ServerWorld world, Vec3d pos, Vec2f facing) {
        return new StoredLocation(
            world.getRegistryKey(),
            pos.x, pos.y, pos.z,
            facing.x, facing.y
        );
    }

    public static StoredLocation fromPlayer(ServerPlayerEntity player, BlockPos pos) {
        var yaw = player.getYaw();
        var pitch = player.getPitch();
        return fromWorld(player.getEntityWorld(), pos, new Vec2f(yaw, pitch));
    }

    public static @NotNull StoredLocation fromSpawnPoint(WorldProperties.SpawnPoint spawnPoint) {
        return fromSpawnPoint(spawnPoint, Vec3d.ZERO);
    }

    public static @NotNull StoredLocation fromSpawnPoint(WorldProperties.SpawnPoint spawnPoint, Vec3d offset) {
        var pos = spawnPoint.getPos();
        return new StoredLocation(
            spawnPoint.getDimension(),
            pos.getX() + offset.x,
            pos.getY() + offset.y,
            pos.getZ() + offset.z,
            spawnPoint.yaw(),
            spawnPoint.pitch()
        );
    }

    public static StoredLocation fromEntity(Entity destination) {
        return new StoredLocation(
            destination.getEntityWorld().getRegistryKey(),
            destination.getX(), destination.getY(), destination.getZ(),
            destination.getYaw(), destination.getPitch()
        );
    }
}
