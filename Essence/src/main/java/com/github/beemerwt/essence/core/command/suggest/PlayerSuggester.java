package com.github.beemerwt.essence.core.command.suggest;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.command.suggest.backend.AllPlayersBackend;
import com.github.beemerwt.essence.core.command.suggest.backend.Backend;
import com.github.beemerwt.essence.core.command.suggest.backend.BannedBackend;
import com.github.beemerwt.essence.core.command.suggest.backend.ConnectedBackend;
import com.github.beemerwt.essence.core.data.model.PlayerData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player suggester with pluggable backends (connected, DB, DB subsets like banned, muted, etc.).
 * Supports paging via "@<page> <prefix>" and passes raw input through for UUIDs.
 */
public final class PlayerSuggester implements SuggestionProvider<ServerCommandSource> {

    /* ==============================
       Public, ready-to-use instances
       ============================== */

    /** Online players only (from PlayerManager). */
    public static final PlayerSuggester CONNECTED = new PlayerSuggester(new ConnectedBackend());

    /** All players in DB (prefix search). */
    public static final PlayerSuggester DATABASE  = new PlayerSuggester(new AllPlayersBackend());

    /** Example subset: banned players from DB. */
    public static final PlayerSuggester BANNED    = new PlayerSuggester(new BannedBackend());

    /* ==============================
       Paging + parsing
       ============================== */

    private static final int PAGE_SIZE = 10;
    // @<page> <optional-prefix>
    private static final Pattern PAGE_PREFIX = Pattern.compile("^@(\\d+)\\s*(.*)$");

    private final Backend backend;

    private PlayerSuggester(Backend backend) {
        this.backend = backend;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        final var server = ctx.getSource().getServer();
        final var raw = b.getRemaining();

        // Parse page/prefix once for all backends
        final var pp = PageAndPrefix.parse(raw);
        final int requestedPage = pp.page();
        final String prefix = pp.prefix();

        return CompletableFuture.supplyAsync(() -> {
            int total = backend.count(server, prefix);
            int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            int safePage = Math.max(1, Math.min(requestedPage, pages));
            int offset = (safePage - 1) * PAGE_SIZE;

            // Page nav
            if (safePage > 1) {
                b.suggest("@" + (safePage - 1) + " " + prefix, Text.literal("« Prev page"));
            }
            if (safePage < pages) {
                b.suggest("@" + (safePage + 1) + " " + prefix, Text.literal("Next page »"));
            }

            // Names
            backend.streamNames(server, prefix, offset, PAGE_SIZE, b::suggest);

            // Raw passthrough (e.g., UUID literal or full name typed)
            if (!raw.isEmpty() && !raw.startsWith("@")) {
                b.suggest(raw);
            }

            return b.build();
        }, server);
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
        if (online != null) {
            return Optional.of(Essence.getPlayerStore().get(online));
        }

        // 3) Try offline lookup
        return Essence.getPlayerStore().lookup(input);
    }

    /* ==============================
       Internals
       ============================== */

    /** Lightweight page/prefix parser for "@<page> <prefix>" inputs. */
    private record PageAndPrefix(int page, String prefix) {
        static PageAndPrefix parse(String raw) {
            if (raw == null) return new PageAndPrefix(1, "");
            Matcher m = PAGE_PREFIX.matcher(raw);
            if (m.matches()) {
                int p;
                try {
                    p = Math.max(1, Integer.parseInt(m.group(1)));
                } catch (Exception e) {
                    p = 1;
                }
                String px = m.group(2) == null ? "" : m.group(2).trim();
                return new PageAndPrefix(p, px);
            }
            return new PageAndPrefix(1, raw);
        }
    }
}
