package com.github.beemerwt.mcrpg.command.suggest;

import com.github.beemerwt.mcrpg.data.SkillType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SkillSuggester implements SuggestionProvider<ServerCommandSource> {
    public static final SkillSuggester INSTANCE = new SkillSuggester();
    private SkillSuggester() { }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        for (SkillType skill : SkillType.values()) {
            b.suggest(skill.name().toLowerCase(Locale.ROOT));
        }

        return b.buildFuture();
    }

    public static Optional<SkillType> getSkill(CommandContext<ServerCommandSource> ctx, String argName) {
        try {
            String input = ctx.getArgument(argName, String.class);
            return Optional.of(SkillType.valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
