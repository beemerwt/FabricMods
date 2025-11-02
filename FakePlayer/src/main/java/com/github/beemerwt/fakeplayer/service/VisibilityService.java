package com.github.beemerwt.fakeplayer.service;

import com.github.beemerwt.fakeplayer.FakePlayer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VisibilityService {
    private static final Map<UUID, IntSet> HIDDEN = new ConcurrentHashMap<>();

    public static void hideFrom(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        // Immediately despawn it on this client + also remove from player list UI if it's a (fake) player
        sendDespawn(viewer, fake);
        sendPlayerListRemove(viewer, fake);

        HIDDEN.computeIfAbsent(viewer.getUuid(), u -> new IntOpenHashSet())
            .add(fake.getId());
    }

    /** Mark hidden (so spawn & updates are filtered) and also proactively clean up the client. */
    public static void preHideAndDespawn(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        HIDDEN.computeIfAbsent(viewer.getUuid(), u -> new it.unimi.dsi.fastutil.ints.IntOpenHashSet())
            .add(fake.getId());

        // Proactively ensure they're gone clientside (harmless if they never spawned there)
        viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(fake.getUuid())));
        viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(fake.getId()));
    }

    public static void showTo(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        HIDDEN.computeIfAbsent(viewer.getUuid(), u -> new IntOpenHashSet())
            .remove(fake.getId());

        if (viewer == fake) return;  // Don't respawn self

        // For players, ensure they’re in the tab list before spawning the entity
        sendPlayerListAdd(viewer, fake);
        sendSpawnPlayerSequence(viewer, fake);
    }

    public static void showAllTo(ServerPlayerEntity viewer) {
        FakePlayer.getLogger().info("Showing all fake players to {}", viewer.getStringifiedName());
        var world = viewer.getEntityWorld();
        world.getChunkManager().chunkLoadingManager.forEachEntityTrackedBy(viewer, entity -> {
            if (entity instanceof ServerPlayerEntity fake && isHidden(viewer, fake.getId())) {
                showTo(viewer, fake);
            }
        });
    }

    public static boolean isHidden(ServerPlayerEntity viewer, int entityId) {
        var set = HIDDEN.get(viewer.getUuid());
        return set != null && set.contains(entityId);
    }

    // Call on viewer join so they start with fake players hidden by default:
    public static void onViewerJoin(ServerPlayerEntity viewer, Collection<? extends ServerPlayerEntity> fakePlayers) {
        FakePlayer.getLogger().info("Hiding all fake players from {}", viewer.getStringifiedName());
        for (var e : fakePlayers) hideFrom(viewer, e);
    }

    private static void sendDespawn(ServerPlayerEntity viewer, Entity e) {
        viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(e.getId()));
    }

    private static void sendPlayerListRemove(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(fake.getUuid())));
    }

    private static void sendPlayerListAdd(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        viewer.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(fake)));
    }

    private static void sendSpawnPlayerSequence(ServerPlayerEntity viewer, ServerPlayerEntity fake) {
        FakePlayer.getLogger().info("Spawning fake player {} for {}",
            fake.getStringifiedName(), viewer.getStringifiedName());

        // Spawn
        viewer.networkHandler.sendPacket(new EntitySpawnS2CPacket(fake, 0, fake.getBlockPos()));

        // Data tracker (flags, invisibility, pose, etc.)
        viewer.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(fake.getId(),
            fake.getDataTracker().getChangedEntries()));

        // We will not be sending Equipment (armor/hand items)
        // For some reason this crashes the server...

        // Attributes (movement speed, max health, etc.)
        viewer.networkHandler.sendPacket(new EntityAttributesS2CPacket(fake.getId(),
            fake.getAttributes().getAttributesToSend()));

        // Active effects
        fake.getStatusEffects().forEach(effect ->
            viewer.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(fake.getId(), effect, true))
        );

        viewer.networkHandler.sendPacket(new EntityPositionS2CPacket(fake.getId(), EntityPosition.fromEntity(fake),
            Set.of(), fake.isOnGround()));

        viewer.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(fake, (byte) (fake.headYaw * 256.0F / 360.0F)));

        viewer.networkHandler.sendPacket(new EntityPositionSyncS2CPacket(fake.getId(),
            EntityPosition.fromEntity(fake), fake.isOnGround()));

    }
}
