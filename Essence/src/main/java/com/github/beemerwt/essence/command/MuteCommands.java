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

public final class MuteCommands {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "mute");

        // /mute <player> [reason...]
        d.register(literal("mute")
            .requires(src -> Permissions.check(src, "essence.mute", OpLevel.SUPER_MOD))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var target = getPlayerOrError(ctx);
                    return target.map(playerData -> execMute(src, playerData.uuid(), /*expires*/ null,
                            "Muted by " + src.getName(), /*reasonOverride*/ null)).orElse(0);
                })
                .then(argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = getPlayerOrError(ctx);
                        String reason = StringArgumentType.getString(ctx, "reason").trim();
                        return target.map(playerData -> execMute(src, playerData.uuid(), null,
                                "Muted by " + src.getName(), reason.isEmpty() ? null : reason)).orElse(0);
                    })
                )
            )
        );

        // /tempmute <player> <time> [reason...]
        d.register(literal("tempmute")
            .requires(src -> Permissions.check(src, "essence.tempmute", OpLevel.MODERATOR))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.CONNECTED)
                .then(argument("time", TimeArgumentType.time(1))
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = getPlayerOrError(ctx);

                        int timeTicks = IntegerArgumentType.getInteger(ctx, "time");
                        Duration durMillis = Duration.ofMillis(timeTicks * 50L);
                        Instant exp = Instant.now().plus(durMillis);
                        return target.map(playerData -> execMute(src, playerData.uuid(),
                                exp, "Muted by " + src.getName(), null)).orElse(0);
                    })
                    .then(argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            var target = getPlayerOrError(ctx);

                            int timeTicks = IntegerArgumentType.getInteger(ctx, "time");
                            Duration durMillis = Duration.ofMillis(timeTicks * 50L);
                            Instant exp = Instant.now().plus(durMillis);
                            String reason = StringArgumentType.getString(ctx, "reason").trim();
                            return target.map(playerData -> execMute(src, playerData.uuid(), exp,
                                    "Muted by " + src.getName(), reason.isEmpty() ? null : reason)).orElse(0);
                        })
                    )
                )
            )
        );

        // /unmute <player>
        d.register(literal("unmute")
            .requires(src -> Permissions.check(src, "essence.unmute", OpLevel.SUPER_MOD))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var target = getPlayerOrError(ctx);
                    if (target.isEmpty()) return 0;
                    boolean changed = Essence.getSuspensionStore().unmute(target.get().uuid());
                    if (!changed) { src.sendError(Text.literal("Player is not muted.")); return 0; }
                    src.sendFeedback(() -> Text.literal("Unmuted ")
                            .append(Text.literal(target.get().name()).formatted(Formatting.YELLOW)), true);
                    return 1;
                })
            )
        );
    }

    /* ===================== Shared core ===================== */

    private static int execMute(ServerCommandSource src, UUID target, Instant expires,
                                String defaultReason, String reasonOverride) {
        String by = src.getName();
        String reason = reasonOverride != null ? reasonOverride : defaultReason;

        if (expires == null) {
            Essence.getSuspensionStore().mutePermanent(target, actorUuid(src), by, reason);
            feedback(src, target, Text.literal(src.getName()).formatted(Formatting.YELLOW)
                .append(Text.literal(" permanently muted "))
                .append(Text.literal(nameOf(src, target)).formatted(Formatting.YELLOW))
                .append(reasonOverride != null ? Text.literal(": " + reason) : Text.empty()));
        } else {
            Essence.getSuspensionStore().muteTemporary(target, actorUuid(src), by, reason, expires);
            feedback(src, target, Text.literal(src.getName()).formatted(Formatting.YELLOW)
                .append(Text.literal(" temporarily muted "))
                .append(Text.literal(nameOf(src, target)).formatted(Formatting.YELLOW))
                .append(Text.literal(" until " + expires + (reasonOverride != null ? " for: " + reason : ""))));
        }
        return 1;
    }

    private static void feedback(ServerCommandSource src, UUID target, Text msg) {
        src.sendFeedback(() -> msg, true);
        // Optional: notify target if online
        var sp = src.getServer().getPlayerManager().getPlayer(target);
        if (sp != null) sp.sendMessage(Text.literal("You have been muted."), false);
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

    private static String nameOf(ServerCommandSource src, UUID uuid) {
        var sp = src.getServer().getPlayerManager().getPlayer(uuid);
        if (sp != null) return sp.getName().getString();
        try { return Essence.getPlayerStore().get(uuid).name(); }
        catch (Exception e) { return uuid.toString(); }
    }
}
