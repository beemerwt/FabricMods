package com.github.beemerwt.essence.command.suggest;

import com.github.beemerwt.essence.Essence;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.concurrent.CompletableFuture;

public final class JailNameSuggester implements SuggestionProvider<ServerCommandSource> {
    public static final JailNameSuggester INSTANCE = new JailNameSuggester();
    private JailNameSuggester() {}

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        String prefix = b.getRemaining().toLowerCase();
        Essence.getSuspensionStore().listAllJails().keySet().stream()
                .filter(n -> n.startsWith(prefix))
                .forEach(b::suggest);
        return b.buildFuture();
    }
}

