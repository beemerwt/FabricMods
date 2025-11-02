package com.github.beemerwt.essence.core.capability;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.payload.NoclipSyncS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NoclipCapability {
    private static final Set<UUID> CLIENT_SUPPORTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static boolean supported(ServerPlayerEntity p) {
        return CLIENT_SUPPORTED.contains(p.getUuid());
    }

    public static void setSupported(ServerPlayerEntity p, boolean on) {
        if (on) {
            CLIENT_SUPPORTED.add(p.getUuid());
            Essence.getLogger().info("Player {} joined with noclip support", p.getName().getString());
        } else {
            CLIENT_SUPPORTED.remove(p.getUuid());
        }
    }

    public static void sendSyncIfSupported(ServerPlayerEntity p, boolean on) {
        if (NoclipCapability.supported(p)) {
            ServerPlayNetworking.send(p, new NoclipSyncS2CPayload(on));
        }
    }
}

