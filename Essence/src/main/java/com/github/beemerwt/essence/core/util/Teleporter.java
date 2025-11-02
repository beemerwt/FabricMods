package com.github.beemerwt.essence.core.util;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.LocationType;
import com.github.beemerwt.essence.core.data.model.StoredLocation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;

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
        var world = Locations.resolveWorld(dest);
        if (world == null) {
            player.sendMessage(Text.literal("World not found: " + dest.worldKey()), false);
            return;
        }

        player.teleport(world, dest.x(), dest.y(), dest.z(), Set.of(), dest.yaw(), dest.pitch(), false);
    }
}