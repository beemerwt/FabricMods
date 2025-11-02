package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.async.AsyncChunkBroker;
import com.github.beemerwt.essence.core.command.suggest.HomeSuggester;
import com.github.beemerwt.essence.core.command.suggest.PlayerSuggester;
import com.github.beemerwt.essence.core.data.LocationType;
import com.github.beemerwt.essence.core.data.model.PlayerData;
import com.github.beemerwt.essence.core.data.model.StoredLocation;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.Locations;
import com.github.beemerwt.essence.core.util.Teleporter;
import com.github.beemerwt.essence.core.util.DeferredCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec2f;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class HomeCommands {

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "sethome").requires(Perms.SET_HOME.and(ServerCommandSource::isExecutedByPlayer))
            .executes(ctx -> setHome(ctx.getSource(), "home"))
            .then(argument("name", StringArgumentType.word())
                .executes(ctx ->
                    setHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
            )
        );

        d.register(commandLiteral("essence", "home").requires(Perms.HOME.and(ServerCommandSource::isExecutedByPlayer))
            .executes(ctx -> goHome(ctx.getSource(), null))
            .then(argument("name", StringArgumentType.word()).suggests(HomeSuggester.SELF)
                .executes(ctx ->
                    goHome(ctx.getSource(), StringArgumentType.getString(ctx, "name"))
                )
            )
        );

        // Allows teleporting to another player's home
        d.register(commandLiteral("essence", "phome").requires(Perms.HOME.child("other").and(ServerCommandSource::isExecutedByPlayer))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> goHomeOther(ctx.getSource(),
                    PlayerSuggester.getPlayer(ctx, "player").orElse(null),
                    null)
                )

                .then(argument("playerhome", StringArgumentType.word()).suggests(HomeSuggester.OTHER)
                    .executes(ctx -> goHomeOther(ctx.getSource(),
                        PlayerSuggester.getPlayer(ctx, "player").orElse(null),
                        StringArgumentType.getString(ctx, "playerhome")))
                )
            )
        );

        d.register(commandLiteral("essence", "delhome").requires(Perms.DEL_HOME.and(ServerCommandSource::isExecutedByPlayer))
            .then(argument("name", StringArgumentType.word()).suggests(HomeSuggester.SELF)
                .executes(ctx ->
                    delHome(ctx.getSource(), StringArgumentType.getString(ctx, "name"))
                )
            )
        );
    }

    private static boolean isWorldSpawn(@Nullable StoredLocation loc) {
        if (loc == null) return true;
        var spawnWorld = Locations.resolveWorld(loc);
        if (spawnWorld == null) return true;
        var worldSpawn = Locations.fromSpawnPoint(spawnWorld.getSpawnPoint());
        return worldSpawn.equals(loc);
    }

    /**
     * Set a home location.
     * @param src The player source
     * @param name The home name
     * @return 1 on success, 0 on failure
     */
    private static int setHome(ServerCommandSource src, String name) {
        if ("bed".equalsIgnoreCase(name)) {
            src.sendError(Text.literal("'bed' is reserved and managed automatically."));
            return 0;
        }

        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        boolean ok = Essence.getLocationStore().set(p.getUuid(), LocationType.HOME, name, Locations.capture(p));
        if (!ok) {
            src.sendError(Text.literal("An internal error has occurred. Failed to set home."));
            return 0;
        }

        src.sendFeedback(() -> Text.literal("Home set: " + name), false);
        return 1;
    }

    /**
     * Go to a home location.
     * @param src The player source
     * @param name The home name, or null for bed
     * @return 1 on success, 0 on failure
     */
    private static int goHome(ServerCommandSource src, @Nullable String name) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("This command can only be used in-game."));
            return 0;
        }

        if (name == null || name.equalsIgnoreCase("bed")) {
            var respawn = p.getRespawn();
            if (respawn == null) {
                src.sendError(Text.literal("Bed is missing or obstructed."));
                return 0;
            }

            var respawnLoc = Locations.fromSpawnPoint(respawn.respawnData());
            if (isWorldSpawn(respawnLoc)) {
                src.sendError(Text.literal("Bed is missing or obstructed."));
                return 0;
            }

            teleportToBed(DeferredCommand.defer(src), null, respawnLoc);
            return 1;
        }

        StoredLocation loc = Essence.getLocationStore().get(p.getUuid(), LocationType.HOME, name).orElse(null);
        if (loc == null) {
            src.sendError(Text.literal("No home named '" + name + "'."));
            return 0;
        }

        Teleporter.teleportSavingBack(p, loc);
        src.sendFeedback(() -> Text.literal("Teleported to home: " + name), false);
        return 1;
    }

    /**
     * Go to another player's home location.
     * @param src The player source
     * @param targetData The target player's data
     * @param homeName The home name, or null for bed
     * @return 1 on success, 0 on failure
     */
    private static int goHomeOther(ServerCommandSource src, @Nullable PlayerData targetData, @Nullable String homeName) {
        if (targetData == null) {
            src.sendError(Text.literal("Target player not found."));
            return 0;
        }

        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;

        if (homeName == null || homeName.equalsIgnoreCase("bed")) {
            var respawn = Essence.getLocationStore().getSingle(targetData.uuid(), LocationType.SPAWN).orElse(null);
            if (isWorldSpawn(respawn)) {
                src.sendError(Text.literal("Bed for player " + targetData.name() + " is missing or obstructed."));
                return 0;
            }

            teleportToBed(DeferredCommand.defer(src), targetData.name(), respawn);
            return 1;
        }

        StoredLocation loc = Essence.getLocationStore().get(targetData.uuid(), LocationType.HOME, homeName).orElse(null);
        if (loc == null) {
            src.sendError(Text.literal("Player " + targetData.name() + " has no home named " + homeName + "."));
            return 0;
        }

        Teleporter.teleportSavingBack(p, loc);
        src.sendFeedback(() -> Text.literal("Teleported to " + homeName + " home of " + targetData.name()), false);
        return 1;
    }

    /**
     * Delete a home location.
     * @param src The player source
     * @param name The home name
     * @return 1 on success, 0 on failure
     */
    private static int delHome(ServerCommandSource src, String name) {
        if ("bed".equalsIgnoreCase(name)) {
            src.sendError(Text.literal("You cannot delete the reserved 'bed' home. Sleep in a bed to update it."));
            return 0;
        }
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;

        var deleted = Essence.getLocationStore().delete(p.getUuid(), LocationType.HOME, name);
        if (deleted) {
            src.sendFeedback(() -> Text.literal("Deleted home: " + name), false);
            return 1;
        }

        src.sendError(Text.literal("No home named '" + name + "'."));
        return 0;
    }

    /**
     * Get a safe respawn location near the bed.
     * Should only ever be called when the chunk containing the bed is loaded.
     * @param src The command source
     * @param bedLoc The bed location
     * @return The safe respawn location, or empty if none found
     */
    private static Optional<StoredLocation> getBedRespawnLocation(ServerCommandSource src, StoredLocation bedLoc) {
        var world = Locations.resolveWorld(bedLoc);
        if (world == null) return Optional.empty();

        var player = src.getPlayer();
        if (player == null) return Optional.empty(); // should never happen, we handle this before

        // Locate nearest valid bed wake-up position
        var bedPos = bedLoc.getBlockPos();
        var direction = BedBlock.getDirection(world, bedPos);
        if (direction == null) {
            Essence.getLogger().debug("Player {} has no valid bed at respawn location", player.getStringifiedName());
            return Optional.empty();
        }

        var wakePos = BedBlock.findWakeUpPosition(EntityType.PLAYER, world, bedPos, direction, 0.0f).orElse(null);
        if (wakePos == null) {
            Essence.getLogger().debug("Player {} has no valid wake-up position at bed", player.getStringifiedName());
            return Optional.empty();
        }

        Vec2f facing = new Vec2f(player.getYaw(), player.getPitch());
        return Optional.of(Locations.fromWorld(world, wakePos, facing));
    }


    private static void teleportToBed(DeferredCommand deferred, @Nullable String targetName, StoredLocation bedLoc) {
        var src = deferred.src();
        var p = deferred.player();
        if (p == null) return;

        int cx = (int)bedLoc.x() >> 4;
        int cz = (int)bedLoc.z() >> 4;
        AsyncChunkBroker.enqueue(p.getUuid(), p.getEntityWorld(),
            new ChunkPos(cx, cz),
            Duration.ofSeconds(8),
            chunk -> {
                // Now that the chunk is loaded, find a safe respawn location
                var safeLoc = getBedRespawnLocation(deferred.src(), bedLoc);
                if (safeLoc.isEmpty()) {

                    if (targetName != null) {
                        deferred.src().sendError(Text.literal("Bed for player " + targetName + " is missing or obstructed."));
                    } else
                        deferred.src().sendError(Text.literal("Bed is missing or obstructed."));
                    return;
                }

                Teleporter.teleportSavingBack(p, safeLoc.get());

                if (targetName != null)
                    src.sendFeedback(() -> Text.literal("Teleported to bed of " + targetName), false);
                else
                    src.sendFeedback(() -> Text.literal("Teleported to bed"), false);
            },
            () -> deferred.src().sendError(Text.literal("Timed out waiting to teleport to bed."))
        );
    }
}
