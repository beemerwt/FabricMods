package com.github.beemerwt.essence.command.suggest;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.PlayerData;
import com.github.beemerwt.essence.data.model.PlayerStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerSuggester implements SuggestionProvider<ServerCommandSource> {
    private static final int PAGE_SIZE = 10;
    private static final Pattern PAGE_PREFIX = Pattern.compile("^@(\\d+)\\s*(.*)$"); // @<page> <prefix?>

    public static final PlayerSuggester CONNECTED = new PlayerSuggester(true);
    public static final PlayerSuggester DATABASE = new PlayerSuggester(false);

    private final boolean onlyConnected;

    private PlayerSuggester(boolean onlyConnected) {
        this.onlyConnected = onlyConnected;
    }

    public CompletableFuture<Suggestions> getSuggestionsDatabase(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        String raw = b.getRemaining();
        int page = 1;
        String prefix = raw;

        Matcher m = PAGE_PREFIX.matcher(raw);
        if (m.matches()) {
            try { page = Math.max(1, Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
            prefix = m.group(2) == null ? "" : m.group(2).trim();
        }

        final int p = page;
        final String q = prefix;
        MinecraftServer server = ctx.getSource().getServer();

        return CompletableFuture.supplyAsync(() -> {
            PlayerStore store = Essence.getPlayerStore();
            int total = store.countByPrefix(q);
            int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            int safePage = Math.min(p, pages);
            int offset = (safePage - 1) * PAGE_SIZE;

            if (safePage > 1)
                b.suggest("@" + (safePage - 1) + " " + q, Text.literal("« Prev page"));
            if (safePage < pages)
                b.suggest("@" + (safePage + 1) + " " + q, Text.literal("Next page »"));

            List<PlayerData> rows = store.listByPrefix(q, offset, PAGE_SIZE);
            for (PlayerData pd : rows)
                b.suggest(pd.name());

            if (!raw.isEmpty() && !raw.startsWith("@"))
                b.suggest(raw); // passthrough for manual UUIDs

            return b.build();
        }, server);
    }

    public CompletableFuture<Suggestions> getSuggestionsConnected(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        String raw = b.getRemaining();
        int page = 1;
        String prefix = raw;

        Matcher m = PAGE_PREFIX.matcher(raw);
        if (m.matches()) {
            try { page = Math.max(1, Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
            prefix = m.group(2) == null ? "" : m.group(2).trim();
        }

        final int p = page;
        final String q = prefix;
        MinecraftServer server = ctx.getSource().getServer();

        return CompletableFuture.supplyAsync(() -> {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            // Filter by prefix
            if (!q.isEmpty()) {
                String ql = q.toLowerCase();
                players = players.stream()
                        .filter(pl -> pl.getName().getString().toLowerCase().startsWith(ql))
                        .toList();
            }

            int total = players.size();
            int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            int safePage = Math.min(p, pages);
            int offset = (safePage - 1) * PAGE_SIZE;

            if (safePage > 1)
                b.suggest("@" + (safePage - 1) + " " + q, Text.literal("« Prev page"));
            if (safePage < pages)
                b.suggest("@" + (safePage + 1) + " " + q, Text.literal("Next page »"));

            players.stream()
                    .skip(offset)
                    .limit(PAGE_SIZE)
                    .forEach(pl -> b.suggest(pl.getName().getString()));

            if (!raw.isEmpty() && !raw.startsWith("@"))
                b.suggest(raw); // passthrough for manual UUIDs

            return b.build();
        }, server);
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        if (!onlyConnected)
            return getSuggestionsDatabase(ctx, b);
        else
            return getSuggestionsConnected(ctx, b);
    }

    /**
     * Convenience lookup for command executors.
     * Accepts online names, UUIDs, or offline names from the player DB.
     */
    public static Optional<PlayerData> getPlayer(CommandContext<ServerCommandSource> ctx, String argName) {
        String input = StringArgumentType.getString(ctx, argName);

        // 1) Try parse UUID
        try {
            UUID id = UUID.fromString(input);
            return Optional.of(Essence.getPlayerStore().get(id));
        } catch (IllegalArgumentException ignored) {}

        // 2) Try online
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(input);
        if (online != null)
            return Optional.of(Essence.getPlayerStore().get(online));

        // 3) Try offline lookup
        return Essence.getPlayerStore().lookup(input);
    }
}
