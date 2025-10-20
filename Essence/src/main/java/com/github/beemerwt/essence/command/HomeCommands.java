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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HomeCommands {
    private static final SuggestionProvider<ServerCommandSource> HOME_SUGGESTIONS =
            (ctx, b) -> {
                if (ctx.getSource().getPlayer() == null) return b.buildFuture();
                var sp = ctx.getSource().getPlayer();

                var homes = Essence.getLocationStore().list(sp.getUuid(), LocationType.HOME);
                homes.keySet().forEach(b::suggest);
                return b.buildFuture();
            };

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("sethome")
                .requires(src -> Permissions.check(src, "essence.sethome", OpLevel.SUPER_MOD))
                .executes(ctx -> setHome(ctx.getSource(), "home"))
                .then(argument("name", StringArgumentType.word())
                        .suggests(HOME_SUGGESTIONS)
                        .executes(ctx ->
                                setHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                )
        );

        d.register(literal("home")
                .requires(src -> Permissions.check(src, "essence.home", OpLevel.NONE))
                .executes(ctx -> goHomeDefault(ctx.getSource())) // default: bed, then home
                .then(argument("name", StringArgumentType.word()).suggests(HOME_SUGGESTIONS)
                        .executes(ctx ->
                                goHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                )
        );

        d.register(literal("delhome")
                .requires(src -> Permissions.check(src, "essence.delhome", OpLevel.SUPER_MOD))
                .then(argument("name", StringArgumentType.word())
                        .suggests(HOME_SUGGESTIONS)
                        .executes(ctx ->
                                delHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                )
        );
    }

    // Default /home behavior: prioritize "bed", then "home".
    private static int goHomeDefault(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;

        var bed = Essence.getLocationStore().getSingle(p.getUuid(), LocationType.SPAWN);
        if (bed.isPresent()) {
            Teleporter.teleportSavingBack(p, bed.get());
            src.sendFeedback(() -> Text.literal("Teleported to bed."), false);
            return 1;
        }

        src.sendError(Text.literal("No bed or default home set."));
        return 0;
    }

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

    private static int goHome(ServerCommandSource src, String name) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;

        StoredLocation loc = null;
        if (name.equalsIgnoreCase("bed")) {
            loc = Essence.getLocationStore().getSingle(p.getUuid(), LocationType.SPAWN).orElse(null);
        } else {
            loc = Essence.getLocationStore().get(p.getUuid(), LocationType.HOME, name).orElse(null);
        }

        if (loc == null) {
            src.sendError(Text.literal("No home named '" + name + "'."));
            return 0;
        }

        Teleporter.teleportSavingBack(p, loc);
        src.sendFeedback(() -> Text.literal("Teleported to home: " + name), false);
        return 1;
    }

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
}
