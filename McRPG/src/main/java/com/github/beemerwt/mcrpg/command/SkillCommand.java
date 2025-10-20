package com.github.beemerwt.mcrpg.command;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.command.suggest.PlayerSuggester;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.SkillLinks;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.github.beemerwt.mcrpg.util.CommandUtils.fail;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SkillCommand {
    private SkillCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("skills")
            .executes(SkillCommand::execSelf)
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(SkillCommand::execOther))
        );
    }

    private static int execSelf(CommandContext<ServerCommandSource> ctx) {
        try {
            var self = ctx.getSource().getPlayer();
            if (self == null) return fail(ctx, "Cannot be run from console.");

            var data = McRPG.getStore().get(self);
            renderSkills(ctx, data);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            return fail(ctx, "Internal error: " + e.getMessage());
        }
    }

    private static int execOther(CommandContext<ServerCommandSource> ctx) {
        try {
            String name = StringArgumentType.getString(ctx, "player");

            var player = McRPG.getStore().lookup(name);
            if (player.isEmpty()) return fail(ctx, "Player not found: " + name);

            renderSkills(ctx, player.get());
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            return fail(ctx, "Internal error: " + e.getMessage());
        }
    }

    // -------------- presentation --------------

    private static void renderSkills(CommandContext<ServerCommandSource> ctx, PlayerData pd) {
        // Header
        ctx.getSource().sendFeedback(() ->
            Text.literal("Skills for " + pd.getName()).formatted(Formatting.GOLD), false);

        // One line per skill: Level (Total XP)
        for (SkillType s : SkillType.values()) {
            int level = Leveling.getLevel(pd, s);
            var text = Text.literal(" - " + s.name() + ": ")
                .append(Text.literal("Lv " + level).formatted(Formatting.GREEN));

            if (!SkillLinks.isComposite(s)) {
                long total = Leveling.getRawTotalXp(pd, s);
                text = text.append(Text.literal("  (" + total + " XP)").formatted(Formatting.GRAY));
            }

            final var line = text;
            ctx.getSource().sendFeedback(() -> line, false);
        }
    }
}
