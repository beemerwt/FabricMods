package com.github.beemerwt.essence.core.data.model;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record StoredLocation(
        RegistryKey<World> worldKey,
        double x, double y, double z,
        float yaw, float pitch
) {
    public StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this(RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(world)), x, y, z, yaw, pitch);
    }

    public Vec3d getPos() { return new Vec3d(x, y, z); }
    public BlockPos getBlockPos() { return BlockPos.ofFloored(x, y, z); }
    public Vec2f getFacing() { return new Vec2f(yaw, pitch); }
}
