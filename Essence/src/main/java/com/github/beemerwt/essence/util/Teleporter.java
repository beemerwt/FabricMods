package com.github.beemerwt.essence.util;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.data.StoredLocation;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class Teleporter {
    private Teleporter() {}

    // For /back: swap current with stored, then go
    public static void teleportSavingBack(ServerPlayerEntity p, StoredLocation dest) {
        var current = Locations.capture(p);
        if (!Essence.getLocationStore().setSingle(p.getUuid(), LocationType.BACK, current))
            Essence.getLogger().warning("Failed to save back location for {}", p.getStringifiedName());
        teleportTo(p, dest);
    }

    public static void teleportTo(ServerPlayerEntity player, StoredLocation dest) {
        // Get the world from the string key
        var world = Locations.resolveWorld(dest.worldKey());
        if (world == null) {
            player.sendMessage(Text.literal("World not found: " + dest.worldKey()), false);
            return;
        }

        var posFlags = PositionFlag.ofDeltaPos(false, false, false);
        posFlags = PositionFlag.combine(posFlags, PositionFlag.ofRot(false, false));
        player.teleport(world, dest.x(), dest.y(), dest.z(), posFlags, dest.yaw(), dest.pitch(), false);
    }
}