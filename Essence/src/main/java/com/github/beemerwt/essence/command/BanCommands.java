package com.github.beemerwt.essence.command;
import static net.minecraft.server.command.CommandManager.*;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.command.suggest.PlayerSuggester;
import com.github.beemerwt.essence.data.PlayerData;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.CommandOverrideUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class BanCommands {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "ban");

        // /ban <player> [reason...]
        d.register(literal("ban")
            .requires(src -> Permissions.check(src, "essence.ban", OpLevel.SUPER_MOD))
            .then(argument("player", StringArgumentType.word())
                .suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var target = getPlayerOrError(ctx);
                    return target.map(playerData -> execBan(src, playerData.uuid(), /*expires*/ null,
                            /*reason*/ "Banned by " + src.getName(),
                            /*reasonOverride*/ null)).orElse(0);
                })
                .then(argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = getPlayerOrError(ctx);
                        String reason = StringArgumentType.getString(ctx, "reason").trim();
                        return target.map(playerData -> execBan(src, playerData.uuid(), null,
                                /*defaultReason*/ "Banned by " + src.getName(),
                                /*reasonOverride*/ reason.isEmpty() ? null : reason)).orElse(0);
                    })
                )
            )
        );

        // /tempban <player> <time> [reason...]
        d.register(literal("tempban")
            .requires(src -> Permissions.check(src, "essence.tempban", OpLevel.SUPER_MOD))
            .then(argument("player", StringArgumentType.word())
                .suggests(PlayerSuggester.DATABASE)
                .then(argument("time", TimeArgumentType.time(1)) // > 0
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = getPlayerOrError(ctx);

                        int timeTicks = IntegerArgumentType.getInteger(ctx, "time");
                        Duration durMillis = Duration.ofMillis(timeTicks * 50L); // 1 tick = 50 ms
                        Instant exp = Instant.now().plus(durMillis);
                        return target.map(playerData -> execBan(src, playerData.uuid(), exp,
                                /*defaultReason*/ "Banned by " + src.getName(),
                                /*reasonOverride*/ null)).orElse(0);
                    })
                    .then(argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            var target = getPlayerOrError(ctx);
                            int durTicks = IntegerArgumentType.getInteger(ctx, "time");
                            Duration durMillis = Duration.ofMillis(durTicks * 50L); // 1 tick = 50 ms
                            Instant exp = Instant.now().plus(durMillis);
                            String reason = StringArgumentType.getString(ctx, "reason").trim();
                            return target.map(playerData -> execBan(src, playerData.uuid(), exp,
                                    /*defaultReason*/ "Banned by " + src.getName(),
                                    /*reasonOverride*/ reason.isEmpty() ? null : reason)).orElse(0);
                        })
                    )
                )
            )
        );

        d.register(
            literal("unban")
                .requires(src -> Permissions.check(src, "essence.unban", OpLevel.SUPER_MOD))
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = getPlayerOrError(ctx);
                        if (target.isEmpty()) return 0;
                        var playerData = target.get();

                        var store = Essence.getSuspensionStore();
                        var ban = store.getActiveBan(playerData.uuid());
                        if (ban.isEmpty()) {
                            src.sendError(Text.literal("That player is not banned."));
                            return 0;
                        }

                        store.unban(ban.get().player());
                        src.sendFeedback(() -> Text.literal(src.getName()).formatted(Formatting.YELLOW)
                                .append(Text.literal(" unbanned "))
                                .append(Text.literal(nameOf(src, playerData.uuid())).formatted(Formatting.YELLOW)), true);
                        return 1;
                    })
                )
        );
    }

    /* ===================== Helpers ===================== */

    private static int execBan(ServerCommandSource src, UUID target, Instant expires,
                               String defaultReason, String reasonOverride) {
        String by = src.getName();
        String reason = reasonOverride != null ? reasonOverride : defaultReason;

        if (expires == null) {
            Essence.getSuspensionStore().banPermanent(target, actorUuid(src), by, reason);
            kickIfOnline(src, target, "You have been permanently banned.\nReason: " + reason);
            src.sendFeedback(() -> Text.literal(src.getName()).formatted(Formatting.YELLOW)
                    .append(Text.literal(" permanently banned "))
                    .append(Text.literal(nameOf(src, target)).formatted(Formatting.YELLOW))
                    .append(reasonOverride != null ? Text.literal(": " + reason) : Text.empty()), true);
        } else {
            Essence.getSuspensionStore().banTemporary(target, actorUuid(src), by, reason, expires);
            kickIfOnline(src, target, "Temporarily banned until " + expires + "\nReason: " + reason);
            src.sendFeedback(() -> Text.literal(src.getName()).formatted(Formatting.YELLOW)
                    .append(Text.literal(" temporarily banned "))
                    .append(Text.literal(nameOf(src, target)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" until " + expires + (reasonOverride != null ? " for: " + reason : ""))), true);
        }

        return 1;
    }

    private static Optional<PlayerData> getPlayerOrError(CommandContext<ServerCommandSource> ctx) {
        var src = ctx.getSource();
        var opt = PlayerSuggester.getPlayer(ctx, "player");
        if (opt.isEmpty()) src.sendError(Text.literal("Player not found."));
        return opt;
    }

    private static UUID actorUuid(ServerCommandSource src) {
        var sp = src.getPlayer();
        return sp != null ? sp.getUuid() : null;
    }

    private static void kickIfOnline(ServerCommandSource src, UUID target, String msg) {
        var sp = src.getServer().getPlayerManager().getPlayer(target);
        if (sp != null) sp.networkHandler.disconnect(Text.literal(msg));
    }

    /** Best-effort display name for feedback without re-querying DB elsewhere. */
    private static String nameOf(ServerCommandSource src, UUID uuid) {
        var sp = src.getServer().getPlayerManager().getPlayer(uuid);
        if (sp != null) return sp.getName().getString();
        try { return Essence.getPlayerStore().get(uuid).name(); }
        catch (Exception e) { return uuid.toString(); }
    }
}
