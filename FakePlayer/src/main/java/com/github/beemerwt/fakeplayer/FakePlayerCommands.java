package com.github.beemerwt.fakeplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
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

public final class FakePlayerCommands {
    private FakePlayerCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("fakeplayer")
                .requires(src -> src.hasPermissionLevel(3))

                // /fakeplayer spawn <name> [x y z] [yaw] [pitch] [dimensionId]
                .then(CommandManager.literal("spawn")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> safeExec(ctx.getSource(), () -> {
                                    var src = ctx.getSource();
                                    var server = src.getServer();
                                    var world = src.getWorld();
                                    var pos = BlockPos.ofFloored(src.getPosition());
                                    return doSpawn(server, world, pos, 0f, 0f, StringArgumentType.getString(ctx, "name"), src);
                                }))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> safeExec(ctx.getSource(), () -> {
                                            var src = ctx.getSource();
                                            var server = src.getServer();
                                            var world = src.getWorld();
                                            var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                            return doSpawn(server, world, pos, 0f, 0f, StringArgumentType.getString(ctx, "name"), src);
                                        }))
                                        .then(CommandManager.argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
                                                .then(CommandManager.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                                                        .then(CommandManager.argument("dimension", StringArgumentType.word())
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

                // /fakeplayer list
                .then(CommandManager.literal("list")
                        .executes(ctx -> safeExec(ctx.getSource(), () -> {
                            var list = FakePlayerRegistry.list();
                            if (list.isEmpty()) {
                                ctx.getSource().sendFeedback(() -> Text.literal("No fake players."), false);
                            } else {
                                String names = list.stream()
                                        .map(p -> p.getGameProfile().name())
                                        .sorted()
                                        .reduce((a, b) -> a + ", " + b)
                                        .orElse("");
                                ctx.getSource().sendFeedback(() -> Text.literal("Fake players: " + names), false);
                            }
                            return 1;
                        }))
                )

                // /fakeplayer remove <name>
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, sb) -> {
                                    // Get all fakeplayer names
                                    var names = FakePlayerRegistry.list().stream()
                                        .map(p -> p.getGameProfile().name())
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

    private static int doSpawn(MinecraftServer server, ServerWorld world, BlockPos pos, float yaw, float pitch, String name, ServerCommandSource src) {
        var player = FakePlayerRegistry.spawn(server, name, world, pos, yaw, pitch);
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
        var regKey = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id);
        ServerWorld w = server.getWorld(regKey);
        if (w == null) {
            throw new IllegalArgumentException("Unknown dimension: " + key);
        }
        return w;
    }
}
