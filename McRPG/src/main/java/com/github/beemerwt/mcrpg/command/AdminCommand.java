package com.github.beemerwt.mcrpg.command;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.command.suggest.PlayerSuggester;
import com.github.beemerwt.mcrpg.command.suggest.SkillSuggester;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.permission.OpLevel;
import com.github.beemerwt.mcrpg.permission.Permissions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

import static com.github.beemerwt.mcrpg.util.CommandUtils.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminCommand {
    private static final SuggestionProvider<ServerCommandSource> ON_OFF_SUGGESTIONS = (context, builder) -> {
        builder.suggest("on", Text.literal("Enable debug mode"));
        builder.suggest("off", Text.literal("Disable debug mode"));
        return builder.buildFuture();
    };

    private AdminCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("mcrpg")
            .requires(src -> Permissions.check(src, "mcrpg.admin", OpLevel.ADMIN))

            .then(literal("debug")
                .then(argument("enabled", StringArgumentType.word()).suggests(ON_OFF_SUGGESTIONS)
                .executes(ctx -> safe(ctx, () -> {
                    var onOff = StringArgumentType.getString(ctx, "enabled");
                    if (!onOff.equals("on") && !onOff.equals("off"))
                        return fail(ctx, "Invalid argument: " + onOff + ". Use 'on' or 'off'.");

                    var enabled = onOff.equals("on");
                    ConfigManager.setDebug(enabled);
                    return ok(ctx, "Debug " + (enabled ? "enabled" : "disabled") + ".", false);
                }))))

            .then(literal("reload")
                .executes(ctx -> safe(ctx, () -> {
                    try {
                        reload(ctx);
                        return ok(ctx, "McRPG reloaded.", true);
                    } catch (Exception e) {
                        return fail(ctx, "Reload failed: " + e.getMessage());
                    }
                })))

            .then(literal("resetcd")
                .executes(ctx -> safe(ctx, () -> {
                    var player = ctx.getSource().getPlayer();
                    if (player == null) return fail(ctx, "Run this in-game.");
                    try {
                        AbilityManager.clearAllFor(player.getUuid());
                        return ok(ctx, "Reset all ability cooldowns.", false);
                    } catch (Exception e) {
                        return fail(ctx, "Failed to reset cooldowns: " + e.getMessage());
                    }
                })))

            .then(literal("setlevel")
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .then(argument("skill", StringArgumentType.word()).suggests(SkillSuggester.INSTANCE)
                .then(argument("level", IntegerArgumentType.integer(0))
                .executes(AdminCommand::execSetLevel)))))

            .then(literal("addxp")
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .then(argument("skill", StringArgumentType.word()).suggests(SkillSuggester.INSTANCE)
                .then(argument("amount", LongArgumentType.longArg(1))
                .executes(AdminCommand::execAddXp)))))

            .then(literal("subxp")
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .then(argument("skill", StringArgumentType.word()).suggests(SkillSuggester.INSTANCE)
                .then(argument("amount", LongArgumentType.longArg(1))
                .executes(AdminCommand::execSubXp)))))
        );
    }

    // ---------------- executes ----------------

    private static int execSetLevel(CommandContext<ServerCommandSource> ctx) {
        return safe(ctx, () -> {
            var name  = StringArgumentType.getString(ctx, "player");
            var raw   = StringArgumentType.getString(ctx, "skill");
            int level = IntegerArgumentType.getInteger(ctx, "level");

            Optional<SkillType> optSkill = SkillType.parseSkill(raw);
            if (optSkill.isEmpty()) return fail(ctx, "Invalid skill: " + raw);
            SkillType skill = optSkill.get();

            var player = McRPG.getStore().lookup(name);
            if (player.isEmpty()) return fail(ctx, "Player not found: " + name);

            var online = McRPG.getServer().getPlayerManager().getPlayer(name);
            if (online != null) {
                online.sendMessage(Text.literal("Your " + skill.getName() + " level was set to " + level + ".")
                    .formatted(Formatting.YELLOW), false);
            }

            Leveling.setLevel(player.get(), skill, level);
            return ok(ctx, "Set " + name + " " + skill + " to level " + level + ".", false);
        });
    }

    private static int execAddXp(CommandContext<ServerCommandSource> ctx) {
        return safe(ctx, () -> {
            String name = StringArgumentType.getString(ctx, "player");
            String raw  = StringArgumentType.getString(ctx, "skill");
            long amount = LongArgumentType.getLong(ctx, "amount");
            if (amount == 0) return fail(ctx, "Amount must be non-zero.");

            Optional<SkillType> optSkill = SkillType.parseSkill(raw);
            if (optSkill.isEmpty()) return fail(ctx, "Invalid skill: " + raw);
            SkillType skill = optSkill.get();

            var player = McRPG.getStore().lookup(name);
            if (player.isEmpty()) return fail(ctx, "Player not found: " + name);

            Leveling.addXp(player.get(), skill, amount);

            var online = McRPG.getServer().getPlayerManager().getPlayer(name);
            if (online != null) {
                online.sendMessage(Text.literal("You received " + amount + " XP in " + skill + ".")
                    .formatted(Formatting.YELLOW), false);
            }

            return ok(ctx, "Gave " + amount + " XP to " + name + " (" + skill + ").", false);
        });
    }

    private static int execSubXp(CommandContext<ServerCommandSource> ctx) {
        return safe(ctx, () -> {
            String name  = StringArgumentType.getString(ctx, "player");
            String raw   = StringArgumentType.getString(ctx, "skill");
            long amount  = LongArgumentType.getLong(ctx, "amount");
            if (amount <= 0) return fail(ctx, "Amount must be non-zero.");

            Optional<SkillType> optSkill = SkillType.parseSkill(raw);
            if (optSkill.isEmpty()) return fail(ctx, "Invalid skill: " + raw);
            SkillType skill = optSkill.get();

            var player = McRPG.getStore().lookup(name);
            if (player.isEmpty()) return fail(ctx, "Player not found: " + name);

            var online = McRPG.getServer().getPlayerManager().getPlayer(name);
            if (online != null) {
                online.sendMessage(Text.literal("You lost " + amount + " XP in " + skill + ".")
                    .formatted(Formatting.YELLOW), false);
            }

            Leveling.addXp(player.get(), skill, -amount);
            return ok(ctx, "Removed " + amount + " XP from " + name + " (" + skill + ").", false);
        });
    }

    // ---------------- helpers ----------------

    public static void reload(CommandContext<ServerCommandSource> ctx) {
        long start = System.nanoTime();
        try {
            ConfigManager.reloadAll();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            ctx.getSource().sendFeedback(() -> Text.literal("McRPG configs reloaded (" + ms + " ms)."), true);
        } catch (Exception ex) {
            ctx.getSource().sendError(Text.literal("Reload failed: " + ex.getMessage()));
        }
    }
}