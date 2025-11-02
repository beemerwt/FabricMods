package com.github.beemerwt.fakeplayer;

import com.github.beemerwt.fakeplayer.service.VisibilityService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.concurrent.Callable;

import static com.github.beemerwt.fakeplayer.FakePlayer.TEAM_NAME;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class FakePlayerCommands {
    private FakePlayerCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("fakeplayer")
            .requires(src -> src.hasPermissionLevel(3))

            // /fakeplayer spawn <name> [x y z] [yaw] [pitch] [dimensionId]
            .then(literal("spawn")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> safeExec(ctx.getSource(), () -> {
                        var src = ctx.getSource();
                        var server = src.getServer();
                        var world = src.getWorld();
                        var pos = BlockPos.ofFloored(src.getPosition());
                        return doSpawn(server, world, pos, 0f, 0f, StringArgumentType.getString(ctx, "name"), src);
                    }))
                    .then(argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> safeExec(ctx.getSource(), () -> {
                            var src = ctx.getSource();
                            var server = src.getServer();
                            var world = src.getWorld();
                            var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                            return doSpawn(server, world, pos, 0f, 0f, StringArgumentType.getString(ctx, "name"), src);
                        }))
                        .then(argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
                            .then(argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                                .then(argument("dimension", StringArgumentType.word())
                                    .executes(ctx -> safeExec(ctx.getSource(), () -> {
                                        var src = ctx.getSource();
                                        var server = src.getServer();
                                        String dim = StringArgumentType.getString(ctx, "dimension");
                                        ServerWorld world = dimensionFromString(server, dim, src);
                                        var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                        float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                                        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                                        return doSpawn(server, world, pos, yaw, pitch, StringArgumentType.getString(ctx, "name"), src);
                                    }))
                                )
                            )
                        )
                    )
                )
            )

            .then(literal("see")
                .requires(src -> src.hasPermissionLevel(3)) // replace with your Perms wrapper if you have one
                .then(CommandManager.literal("on").executes(ctx -> safeExec(ctx.getSource(), () -> setSee(ctx, true))))
                .then(CommandManager.literal("off").executes(ctx -> safeExec(ctx.getSource(), () -> setSee(ctx, false))))
                .then(CommandManager.literal("toggle").executes(ctx -> safeExec(ctx.getSource(), () -> toggleSee(ctx))))
                .executes(ctx -> safeExec(ctx.getSource(), () -> toggleSee(ctx))) // default = toggle
            )

            // /fakeplayer list
            .then(literal("list")
                .executes(ctx -> safeExec(ctx.getSource(), () -> {
                    var list = FakePlayerRegistry.list();
                    if (list.isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal("No fake players."), false);
                    } else {
                        String names = list.stream()
                            .map(PlayerEntity::getStringifiedName)
                            .sorted()
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                        ctx.getSource().sendFeedback(() -> Text.literal("Fake players: " + names), false);
                    }
                    return 1;
                }))
            )

            // /fakeplayer remove <name>
            .then(literal("remove")
                .then(argument("name", StringArgumentType.word())
                    .suggests((ctx, sb) -> {
                        // Get all fakeplayer names
                        var names = FakePlayerRegistry.list().stream()
                            .map(ServerPlayerEntity::getStringifiedName)
                            .sorted()
                            .toList();
                        return CommandSource.suggestMatching(names, sb);
                    })
                    .executes(ctx -> safeExec(ctx.getSource(), () -> {
                        var src = ctx.getSource();
                        boolean ok = FakePlayerRegistry.remove(src.getServer(), StringArgumentType.getString(ctx, "name"));
                        if (ok) src.sendFeedback(() -> Text.literal("Removed."), false);
                        else src.sendError(Text.literal("Not found."));
                        return ok ? 1 : 0;
                    }))
                )
            )
        );
    }

    private static int safeExec(ServerCommandSource src, Callable<Integer> work) {
        try {
            return work.call();
        } catch (Throwable t) {
            t.printStackTrace(); // full stack to console
            src.sendError(Text.literal("[FakePlayer] " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())));
            return 0;
        }
    }

    private static int setSee(CommandContext<ServerCommandSource> ctx, boolean on) {
        ServerPlayerEntity admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        var server = ctx.getSource().getServer();
        Scoreboard board = server.getScoreboard();

        // Ensure the fakeplayers team exists and is configured correctly
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = FakePlayerSpawner.ensureNoEntityCollisionTeam(board);
        }

        var adminTeam = board.getScoreHolderTeam(admin.getNameForScoreboard());

        if (on) {
            if (!team.isEqual(adminTeam)) {
                board.addScoreHolderToTeam(admin.getNameForScoreboard(), team);
                team.setShowFriendlyInvisibles(true);
                ctx.getSource().sendFeedback(() -> Text.literal("You can now see fake players."), false);
            } else {
                ctx.getSource().sendFeedback(() -> Text.literal("You can already see fake players."), false);
            }

            VisibilityService.showAllTo(admin);

        } else {
            if (team.isEqual(adminTeam))  {
                board.removeScoreHolderFromTeam(admin.getNameForScoreboard(), team);
            }

            VisibilityService.onViewerJoin(admin, FakePlayerRegistry.list());
            ctx.getSource().sendFeedback(() -> Text.literal("You will no longer see fake players."), false);
        }

        return 1;
    }

    private static int toggleSee(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        Scoreboard board = ctx.getSource().getServer().getScoreboard();
        var currentTeam = board.getScoreHolderTeam(admin.getNameForScoreboard());
        boolean currentlySeeing = currentTeam != null && TEAM_NAME.equals(currentTeam.getName());
        return setSee(ctx, !currentlySeeing);
    }

    private static int doSpawn(MinecraftServer server, ServerWorld world, BlockPos pos, float yaw, float pitch, String name, ServerCommandSource src) {
        var fake = FakePlayerRegistry.spawn(server, name, world, pos, yaw, pitch);
        var sb = server.getScoreboard();
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer == fake) continue; // skip self

            var team = sb.getScoreHolderTeam(viewer.getNameForScoreboard());
            boolean canSee = (team != null && TEAM_NAME.equals(team.getName()));
            if (canSee) {
                VisibilityService.showTo(viewer, fake);
            } else {
                VisibilityService.preHideAndDespawn(viewer, fake);
            }
        }

        src.sendFeedback(() -> Text.literal("Spawned fake player '" + name + "' at "
            + pos.toShortString() + " in " + world.getRegistryKey().getValue()), true);
        return 1;
    }

    private static ServerWorld dimensionFromString(MinecraftServer server, String key, ServerCommandSource src) {
        // Accept forms like minecraft:overworld, minecraft:the_nether, minecraft:the_end
        Identifier id = Identifier.tryParse(key);
        if (id == null) {
            throw new IllegalArgumentException("Invalid dimension id: " + key);
        }
        var regKey = RegistryKey.of(RegistryKeys.WORLD, id);
        ServerWorld w = server.getWorld(regKey);
        if (w == null) {
            throw new IllegalArgumentException("Unknown dimension: " + key);
        }
        return w;
    }
}
