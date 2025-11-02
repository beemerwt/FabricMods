package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.command.suggest.PlayerSuggester;
import com.github.beemerwt.essence.core.data.model.PlayerData;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.CommandOverrideUtil;
import com.github.beemerwt.essence.core.util.Messenger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.server.command.CommandManager.argument;

public final class BanCommands {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "ban");

        // /ban <player> [reason...]
        d.register(commandLiteral("essence", "ban")
            .requires(Perms.BAN::check)
            .then(argument("player", StringArgumentType.word())
                .suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var target = getPlayerOrError(ctx);
                    return target.map(playerData -> execBan(ctx, playerData.uuid(), null)).orElse(0);
                })
                .then(argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        var target = getPlayerOrError(ctx);
                        String reason = StringArgumentType.getString(ctx, "reason").trim();
                        return target.map(playerData -> execBan(ctx, playerData.uuid(),
                            reason.isEmpty() ? null : reason)).orElse(0);
                    })
                )
            )
        );

        // /tempban <player> <time> [reason...]
        d.register(commandLiteral("essence", "tempban")
            .requires(Perms.TEMP_BAN::check)
            .then(argument("player", StringArgumentType.word())
                .suggests(PlayerSuggester.DATABASE)
                .then(argument("time", TimeArgumentType.time(1)) // > 0
                    .executes(ctx -> {
                        var target = getPlayerOrError(ctx);
                        return target.map(playerData -> execBan(ctx, playerData.uuid(), null)).orElse(0);
                    })
                    .then(argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var target = getPlayerOrError(ctx);
                            String reason = StringArgumentType.getString(ctx, "reason").trim();
                            return target.map(playerData -> execBan(ctx, playerData.uuid(),
                                /*reasonOverride*/ reason.isEmpty() ? null : reason)).orElse(0);
                        })
                    )
                )
            )
        );

        d.register(commandLiteral("essence", "unban")
                .requires(Perms.UNBAN::check)
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.BANNED)
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

        d.register(commandLiteral("essence", "baninfo")
                .requires(Perms.BAN_INFO::check)
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.BANNED)
                    .executes(ctx ->
                        execBanInfo(ctx.getSource(), getPlayerOrError(ctx))
                    )
                )
        );
    }

    /* ===================== Helpers ===================== */

    private static int execBan(CommandContext<ServerCommandSource> ctx, UUID target, String reason) {
        var src = ctx.getSource();
        String by = src.getName();
        Instant expires = null;

        try {
            int durTicks = IntegerArgumentType.getInteger(ctx, "time");
            Duration durMillis = Duration.ofMillis(durTicks * 50L); // 1 tick = 50 ms
            expires = Instant.now().plus(durMillis);
        } catch (IllegalArgumentException ignored) {
        }

        String banMessage = getConfigMessage(src, expires, reason);
        if (expires == null) {
            Essence.getSuspensionStore().banPermanent(target, actorUuid(src), by, reason);
            kickIfOnline(src, target, banMessage);
            Messenger.sendBanNotification(src.getServer(), target, null, reason);
        } else {
            Essence.getSuspensionStore().banTemporary(target, actorUuid(src), by, reason, expires);
            kickIfOnline(src, target, banMessage);
            Messenger.sendBanNotification(src.getServer(), target, expires, reason);
        }

        return 1;
    }

    private static int execBanInfo(ServerCommandSource src, Optional<PlayerData> target) {
        if (target.isEmpty()) {
            src.sendError(Text.literal("Player not found."));
            return 0;
        }

        var store = Essence.getSuspensionStore();
        var ban = store.getActiveBan(target.get().uuid());
        if (ban.isEmpty()) {
            src.sendError(Text.literal("That player is not banned."));
            return 0;
        }

        var b = ban.get();
        Text msg = Text.literal("Ban info for ")
            .append(Text.literal(nameOf(src, target.get().uuid())).formatted(Formatting.YELLOW))
            .append(Text.literal(":\n"))
            .append(Text.literal("  Banned by: " + b.byName() + "\n"))
            .append(Text.literal("  Reason: " + b.reason() + "\n"));
        if (b.expiresAt() != null) {
            msg = msg.copy().append(Text.literal("  Expires at: " + b.expiresAt() + "\n"));
        } else {
            msg = msg.copy().append(Text.literal("  Permanent ban\n"));
        }

        final Text finalMsg = msg;
        src.sendFeedback(() -> finalMsg, false);
        return 1;
    }

    private static @NotNull String getConfigMessage(ServerCommandSource src, Instant expires, String reasonOverride) {
        String defaultReason = expires == null ? Essence.getConfig().defaultPermanentBanMessage
            : Essence.getConfig().defaultTempBanMessage;
        String reason = reasonOverride != null ? reasonOverride : defaultReason;

        reason = reason.replace("{source}", src.getName());
        reason = reason.replace("{reason}", reasonOverride != null ? reasonOverride : "No reason specified.");
        if (expires != null) {
            reason = reason.replace("{expires}", expires.toString());
        }

        return reason;
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

    /**
     * Best-effort display name for feedback without re-querying DB elsewhere.
     */
    private static String nameOf(ServerCommandSource src, UUID uuid) {
        var sp = src.getServer().getPlayerManager().getPlayer(uuid);
        if (sp != null) return sp.getName().getString();
        try {
            return Essence.getPlayerStore().get(uuid).name();
        } catch (Exception e) {
            return uuid.toString();
        }
    }
}
