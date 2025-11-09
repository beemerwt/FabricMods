package com.github.beemerwt.essence.core.util;

import com.github.beemerwt.essence.core.Essence;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HighlightEntity {
    private record Highlight(UUID playerUuid, long expireTick, int[] entityIds, UUID[] entityUuids) {}
    private static final Map<UUID, Highlight> HIGHLIGHTS = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HighlightEntity::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(HighlightEntity::onServerStopping);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var p = handler.player;
            if (p != null) HighlightEntity.clearAllForPlayer(server, p.getUuid());
        });
    }

    public static boolean hasHighlight(UUID uuid) {
        return HIGHLIGHTS.containsKey(uuid);
    }

    public static int clearAllForPlayer(MinecraftServer server, UUID playerUuid) {
        expireFor(server, playerUuid);
        return 1;
    }

    public static void onServerStopping(MinecraftServer server) {
        for (var entry : HIGHLIGHTS.entrySet()) {
            var h = entry.getValue();
            var p = server.getPlayerManager().getPlayer(h.playerUuid());
            if (p != null) for (int id : h.entityIds()) sendClientOnlyDestroy(p, id);
        }
        HIGHLIGHTS.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        long now = server.getTicks();
        Iterator<Map.Entry<UUID, Highlight>> it = HIGHLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var h = entry.getValue();
            if (now >= h.expireTick()) {
                var p = server.getPlayerManager().getPlayer(h.playerUuid());
                if (p != null) for (int id : h.entityIds()) sendClientOnlyDestroy(p, id);
                it.remove();
            }
        }
    }

    // ===== Public entry point: slime-based highlighter =====
    public static int highlightForPlayer(ServerCommandSource source, BlockPos target, int seconds)
        throws CommandSyntaxException
    {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = source.getWorld();

        BlockState state = world.getBlockState(target);
        Block block = state.getBlock();

        if (!BlockClassifier.isContainerBlock(block)) {
            source.sendError(Translate.literal("essence.highlight.error.not_container"));
            return 0;
        }

        // Clear any previous slime highlight for this player
        expireFor(source.getServer(), player.getUuid());

        // Build one or two slimes (client-only)
        if (block instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            // Partner is the chest in front of the chest’s facing
            Direction facing = ChestBlock.getFacing(state);
            BlockPos partner = target.offset(facing);

            var sA = createPhantomBlock(world, target);
            var sB = createPhantomBlock(world, partner);
            if (sA == null || sB == null) {
                source.sendError(Translate.literal("essence.highlight.error.outline_failed"));
                return 0;
            }

            sendClientOnlySpawn(player, sA);
            sendClientOnlySpawn(player, sB);

            long expireTick = world.getServer().getTicks() + seconds * 20L;
            HIGHLIGHTS.put(player.getUuid(), new Highlight(
                player.getUuid(),
                expireTick,
                new int[]{sA.getId(), sB.getId()},
                new UUID[]{sA.getUuid(), sB.getUuid()}
            ));
            source.sendFeedback(() -> Translate.literal("essence.highlight.double_chest_outline_created",
                target.toShortString()), false);
            return 1;

        } else {
            var phantom = createPhantomBlock(world, target);
            if (phantom == null) {
                source.sendError(Translate.literal("essence.highlight.error.outline_failed"));
                return 0;
            }
            sendClientOnlySpawn(player, phantom);

            long expireTick = world.getServer().getTicks() + seconds * 20L;
            HIGHLIGHTS.put(player.getUuid(), new Highlight(
                player.getUuid(),
                expireTick,
                new int[]{phantom.getId()},
                new UUID[]{phantom.getUuid()}
            ));
            source.sendFeedback(() -> Translate.literal("essence.highlight.outline_created",
                target.toShortString()), false);
            return 1;
        }
    }

    // ===== Helper: configure a client-only SlimeEntity “outline cube” =====
    @Nullable
    private static SlimeEntity createPhantomBlock(ServerWorld world, BlockPos pos) {
        SlimeEntity e = EntityType.SLIME.create(world, SpawnReason.COMMAND);
        if (e == null) return null;

        var sb = world.getServer().getScoreboard();
        final Team team = getOrCreateTeam(sb);
        sb.addScoreHolderToTeam(e.getUuid().toString(), team);

        // 1) Use Small slime, then scale it up to just under 1 block
        e.setSize(2, true); // width = 1.04 blocks

        // Keep it quiet and frozen
        e.setAiDisabled(true);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.setNoGravity(true);
        e.setVelocity(Vec3d.ZERO);

        e.noClip = true;

        // 2) Position with feet at block base, lock yaw, zero velocity
        e.refreshPositionAndAngles(feetAtBlock(pos), 0f, 0f);
        lockYaw(e);
        e.setVelocity(Vec3d.ZERO);

        var interp = e.getInterpolator();
        if (interp != null) {
            interp.setLerpDuration(0);
            Essence.getLogger().info("Set lerp duration to zero for slime phantom");
        } else {
            Essence.getLogger().info("No interpolator for slime phantom?");
        }

        // 3) Marker behavior + outline
        e.setInvisible(true);
        e.setGlowing(true);
        return e;
    }

    private static Team getOrCreateTeam(ServerScoreboard sb) {
        var team = sb.getTeam("essence_highlights");
        if (team == null) {
            team = sb.addTeam("essence_highlights");
            team.setColor(Formatting.YELLOW);
            team.setCollisionRule(Team.CollisionRule.NEVER);
        }

        return team;
    }

    /**
     * Sends vanilla spawn + initial tracker update packets to a single player.
     * The entity is NOT added to the world; it will exist only client-side for that one player.
     */
    private static void sendClientOnlySpawn(ServerPlayerEntity player, Entity phantom) {
        var nh = player.networkHandler;

        // Find a position outside of the render distance of the player
        var origin = player.getBlockPos();
        var phantomPos = origin.add(0, 8192, 0);

        // Spawn with initial state
        nh.sendPacket(new EntitySpawnS2CPacket(phantom, 0, phantomPos));

        // Push tracked data (glowing flag, block state for BlockDisplay, etc.)
        var entries = phantom.getDataTracker().getChangedEntries();
        if (entries != null && !entries.isEmpty()) {
            nh.sendPacket(new EntityTrackerUpdateS2CPacket(phantom.getId(), entries));
        }

        nh.sendPacket(new EntityVelocityUpdateS2CPacket(phantom.getId(), phantom.getVelocity()));

        var newPos = EntityPosition.fromTeleportTarget(new TeleportTarget(player.getEntityWorld(),
            phantom.getEntityPos(), phantom.getVelocity(), phantom.getYaw(), phantom.getPitch(),
            TeleportTarget.NO_OP));

        nh.sendPacket(new EntityPositionS2CPacket(phantom.getId(), newPos, Set.of(), false));

        if (phantom instanceof LivingEntity le) {
            Collection<EntityAttributeInstance> attrs = le.getAttributes().getAttributesToSend();
            if (!attrs.isEmpty()) {
                nh.sendPacket(new EntityAttributesS2CPacket(phantom.getId(), attrs));
            }
        }
    }

    private static Vec3d feetAtBlock(BlockPos p) {
        return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    private static void lockYaw(Entity e) {
        e.setYaw((float) 0.0);
        if (e instanceof LivingEntity le) {
            le.setHeadYaw((float) 0.0);
            le.setBodyYaw((float) 0.0);
        }
    }

    /**
     * Sends the destroy packet to remove the phantom entity from the one player's client.
     */
    private static void sendClientOnlyDestroy(ServerPlayerEntity player, int entityId) {
        player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(entityId));
    }

    private static int clearHighlights(ServerCommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity player = src.getPlayerOrThrow();
        expireFor(src.getServer(), player.getUuid());

        src.sendFeedback(() -> Translate.literal("essence.highlight.cleared"), false);
        return 1;
    }

    // ===== Expire (destroy) any active slime highlights for a player =====
    private static void expireFor(MinecraftServer server, UUID playerId) {
        Highlight h = HIGHLIGHTS.remove(playerId);
        if (h == null) return;

        var p = server.getPlayerManager().getPlayer(playerId);
        if (p != null) for (int id : h.entityIds()) sendClientOnlyDestroy(p, id);
    }
}
