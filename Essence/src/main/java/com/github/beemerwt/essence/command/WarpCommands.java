package com.github.beemerwt.essence.command;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.data.StoredLocation;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.Locations;
import com.github.beemerwt.essence.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WarpCommands {
    static final SuggestionProvider<ServerCommandSource> WARP_SUGGESTIONS = (ctx, builder) -> {
        Essence.getLocationStore().list(null, LocationType.WARP).keySet().forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        // /warp <name>
        d.register(
            literal("warp")
                .requires(src -> src.isExecutedByPlayer()
                        && Permissions.check(src, "essence.warp", OpLevel.NONE)) // players only
                .then(argument("name", StringArgumentType.word())
                    .suggests(WARP_SUGGESTIONS)
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        String name = StringArgumentType.getString(ctx, "name");

                        var store = Essence.getLocationStore();
                        var opt = store.get(null, LocationType.WARP, name);
                        if (opt.isEmpty()) {
                            ctx.getSource().sendError(Text.literal("No such warp: " + name));
                            return 0;
                        }

                        StoredLocation loc = opt.get();
                        Teleporter.teleportSavingBack(player, loc);
                        ctx.getSource().sendFeedback(() ->
                            Text.literal("Warped to ").formatted(Formatting.GRAY)
                                .append(Text.literal(name).formatted(Formatting.AQUA)), false);
                        return 1;
                    })
                )
        );

        // /setwarp <name>
        d.register(
            literal("setwarp")
                .requires(ServerCommandSource::isExecutedByPlayer) // players only
                .requires(src -> src.isExecutedByPlayer()
                        && Permissions.check(src, "essence.setwarp", OpLevel.SUPER_MOD))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        String name = StringArgumentType.getString(ctx, "name");

                        StoredLocation loc = Locations.capture(player);
                        var store = Essence.getLocationStore();
                        boolean ok = store.set(null, LocationType.WARP, name, loc);

                        if (!ok) {
                            ctx.getSource().sendError(Text.literal("Failed to save warp: " + name));
                            return 0;
                        }

                        ctx.getSource().sendFeedback(() ->
                            Text.literal("Saved warp ").formatted(Formatting.GRAY)
                                .append(Text.literal(name).formatted(Formatting.AQUA))
                                .append(Text.literal(" @ ").formatted(Formatting.GRAY))
                                .append(Text.literal(prettyPos(loc)).formatted(Formatting.YELLOW)), true);
                        return 1;
                    })
                )
        );

        // /delwarp <name>
        d.register(
            literal("delwarp")
                .requires(src -> src.isExecutedByPlayer()
                        && Permissions.check(src, "essence.delwarp", OpLevel.SUPER_MOD))
                .then(argument("name", StringArgumentType.word())
                    .suggests(WARP_SUGGESTIONS)
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        var store = Essence.getLocationStore();
                        boolean removed = store.delete(null, LocationType.WARP, name);

                        if (!removed) {
                            ctx.getSource().sendError(Text.literal("No such warp: " + name));
                            return 0;
                        }

                        ctx.getSource().sendFeedback(() ->
                            Text.literal("Deleted warp ").formatted(Formatting.GRAY)
                                .append(Text.literal(name).formatted(Formatting.RED)), true);
                        return 1;
                    })
                )
        );
    }

    private static String prettyPos(StoredLocation loc) {
        return String.format("%s %.2f, %.2f, %.2f (%.1f, %.1f)",
                loc.worldKey(), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
    }
}
