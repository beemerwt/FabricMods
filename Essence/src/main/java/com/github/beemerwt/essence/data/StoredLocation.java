package com.github.beemerwt.essence.data;


import com.github.beemerwt.annotation.JanksonObject;
import com.github.beemerwt.essence.util.Locations;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

@JanksonObject
public record StoredLocation(
        String worldKey,
        double x, double y, double z,
        float yaw, float pitch
) {
    public Vec3d getPos() { return new Vec3d(x, y, z); }
    public Vec2f getFacing() { return new Vec2f(yaw, pitch); }
    public Optional<ServerWorld> getWorld() {
        var world = Locations.resolveWorld(this);
        return world != null ? Optional.of(world) : Optional.empty();
    }
    public StoredLocation withPos(BlockPos pos) {
        return new StoredLocation(worldKey, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
    }
}
