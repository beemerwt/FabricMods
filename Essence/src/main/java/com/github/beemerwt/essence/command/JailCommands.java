// JailCommands.java
package com.github.beemerwt.essence.command;

import static net.minecraft.server.command.CommandManager.*;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.command.suggest.JailNameSuggester;
import com.github.beemerwt.essence.command.suggest.PlayerSuggester;
import com.github.beemerwt.essence.data.StoredLocation;
import com.github.beemerwt.essence.data.model.JailRecord;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.text.Paginator;
import com.github.beemerwt.essence.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JailCommands {

    private static final int PAGE_SIZE = 8;

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        // /setjail <name>
        d.register(literal("setjail")
            .requires(src -> Permissions.check(src, "essence.setjail", OpLevel.ADMIN))
            .then(argument("name", StringArgumentType.word())
                .executes(ctx -> {
                    var src = ctx.getSource();
                    if (!(src.getEntity() instanceof ServerPlayerEntity sp)) {
                        src.sendError(Text.literal("Players only."));
                        return 0;
                    }
                    String key = sp.getEntityWorld().getRegistryKey().getValue().toString();
                    StoredLocation loc = new StoredLocation(key, sp.getX(), sp.getY(), sp.getZ(),
                            sp.getYaw(), sp.getPitch());

                    boolean ok = Essence.getSuspensionStore().setJail(StringArgumentType.getString(ctx, "name"), loc);
                    if (!ok) { src.sendError(Text.literal("Failed to save jail.")); return 0; }
                    src.sendFeedback(() -> Text.literal("Jail saved."), true);
                    return 1;
                })
            )
        );

        // /jail <player> <jailname> [time] [reason...]
        d.register(literal("jail")
            .requires(src -> Permissions.check(src, "essence.jail", OpLevel.MODERATOR))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .then(argument("jailname", StringArgumentType.word()).suggests(JailNameSuggester.INSTANCE)
                    // /jail <player> <jailname>  -> uses config default duration, default reason
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = PlayerSuggester.getPlayer(ctx, "player");
                        if (target.isEmpty()) { src.sendError(Text.literal("Player not found.")); return 0; }
                        String jail = StringArgumentType.getString(ctx, "jailname");
                        var loc = Essence.getSuspensionStore().getJail(jail).orElse(null);
                        if (loc == null) { src.sendError(Text.literal("Unknown jail.")); return 0; }

                        Duration def = Duration.ofMinutes(Essence.getConfig().defaultJailDuration); // e.g., PT10M
                        Instant exp = Instant.now().plus(def);
                        String reason = "Jailed by " + src.getName();

                        return execJail(src, target.get().uuid(), target.get().name(), jail, loc, exp, reason);
                    })

                    // /jail <player> <jailname> <time>
                    .then(argument("time", TimeArgumentType.time(1))
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            var target = PlayerSuggester.getPlayer(ctx, "player");
                            if (target.isEmpty()) { src.sendError(Text.literal("Player not found.")); return 0; }
                            String jail = StringArgumentType.getString(ctx, "jailname");
                            var loc = Essence.getSuspensionStore().getJail(jail).orElse(null);
                            if (loc == null) { src.sendError(Text.literal("Unknown jail.")); return 0; }

                            int durTicks = IntegerArgumentType.getInteger(ctx, "time");
                            Duration durMillis = Duration.ofMillis(durTicks * 50L);
                            Instant exp = Instant.now().plus(durMillis);
                            String reason = "Jailed by " + src.getName();

                            return execJail(src, target.get().uuid(), target.get().name(), jail, loc, exp, reason);
                        })
                        // /jail <player> <jailname> <time> <reason...>
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                var src = ctx.getSource();
                                var target = PlayerSuggester.getPlayer(ctx, "player");
                                if (target.isEmpty()) { src.sendError(Text.literal("Player not found.")); return 0; }
                                String jail = StringArgumentType.getString(ctx, "jailname");
                                var loc = Essence.getSuspensionStore().getJail(jail).orElse(null);
                                if (loc == null) { src.sendError(Text.literal("Unknown jail.")); return 0; }

                                int durTicks = IntegerArgumentType.getInteger(ctx, "time");
                                Duration durMillis = Duration.ofMillis(durTicks * 50L);
                                Instant exp = Instant.now().plus(durMillis);
                                String reason = StringArgumentType.getString(ctx, "reason").trim();
                                if (reason.isEmpty()) reason = "Jailed by " + src.getName();
                                return execJail(src, target.get().uuid(), target.get().name(), jail, loc, exp, reason);
                            })
                        )
                    )
                )
            )
        );

        // /unjail <player>
        d.register(literal("unjail")
            .requires(src -> Permissions.check(src, "essence.unjail", OpLevel.MODERATOR))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var target = PlayerSuggester.getPlayer(ctx, "player");
                    if (target.isEmpty()) { src.sendError(Text.literal("Player not found.")); return 0; }
                    boolean changed = Essence.getSuspensionStore().unjail(target.get().uuid());
                    if (!changed) { src.sendError(Text.literal("Player is not jailed.")); return 0; }
                    src.sendFeedback(() -> Text.literal("Unjailed ")
                            .append(Text.literal(target.get().name()).formatted(Formatting.YELLOW)), true);
                    return 1;
                })
            )
        );

        // /jailinfo <player>
        d.register(literal("jailinfo")
            .requires(src -> Permissions.check(src, "essence.jail.view", OpLevel.MODERATOR))
            .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.DATABASE)
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var target = PlayerSuggester.getPlayer(ctx, "player");
                    if (target.isEmpty()) { src.sendError(Text.literal("Player not found.")); return 0; }
                    Optional<JailRecord> r = Essence.getSuspensionStore().getActiveJail(target.get().uuid());
                    if (r.isEmpty()) { src.sendFeedback(() -> Text.literal("Not jailed."), false); return 1; }
                    JailRecord jr = r.get();
                    src.sendFeedback(() -> Text.literal("Jailed player: ").append(Text.literal(target.get().name()).formatted(Formatting.YELLOW))
                            .append(Text.literal("\nJail: " + jr.jailName()))
                            .append(Text.literal("\nBy: " + jr.byName()))
                            .append(Text.literal("\nReason: " + jr.reason()))
                            .append(Text.literal("\nCreated: " + jr.createdAt()))
                            .append(Text.literal("\nExpires: " + jr.expiresAt())), false);
                    return 1;
                })
            )
        );

        // /jaillist [page]
        d.register(literal("jaillist")
            .requires(src -> Permissions.check(src, "essence.jail.view", OpLevel.MODERATOR))
            .executes(ctx -> list(ctx.getSource(), 1))
            .then(argument("page", IntegerArgumentType.integer())
                .executes(ctx -> {
                    int page = IntegerArgumentType.getInteger(ctx, "page");
                    return list(ctx.getSource(), page);
                })
            )
        );
    }

    /* ========================= Core exec ========================= */

    private static int execJail(
            ServerCommandSource src, UUID targetId, String targetName, String jailName,
            StoredLocation jailLoc, Instant expires, String reason
    ) {
        var store = Essence.getSuspensionStore();
        store.jailTemporary(targetId, actorUuid(src), src.getName(), jailName, reason, expires);

        // Teleport if online
        var world = src.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(jailLoc.worldKey())));
        var sp = src.getServer().getPlayerManager().getPlayer(targetId);
        if (world == null) {
            src.sendError(Text.literal("Jail world missing: " + jailLoc.worldKey()));
        } else if (sp != null) {
            Teleporter.teleportTo(sp, jailLoc);
        }

        src.sendFeedback(() -> Text.literal("Jailed ")
            .append(Text.literal(targetName).formatted(Formatting.YELLOW))
            .append(Text.literal(" in '" + jailName + "' until " + expires + " for: " + reason)), true);

        if (sp != null) sp.sendMessage(Text.literal("You have been jailed until " + expires + ". Reason: " + reason), false);
        return 1;
    }

    private static int list(ServerCommandSource src, int page) {
        var store = Essence.getSuspensionStore();
        int total = store.countActiveJails();
        int pages = Math.max(1, (int)Math.ceil(total / (double)PAGE_SIZE));
        page = Math.max(1, Math.min(page, pages));
        int offset = (page - 1) * PAGE_SIZE;
        List<JailRecord> rows = store.listJails(offset, PAGE_SIZE);
        if (rows.isEmpty()) {
            src.sendError(Text.literal("No active jails."));
            return 0;
        }

        final int p = page;
        src.sendFeedback(() -> Paginator.header("Active Jails", p, pages, "/jaillist"), false);
        for (JailRecord r : rows) {
            String name = nameOf(src, r.player());
            src.sendFeedback(() -> Text.literal("- ")
                .append(Text.literal(name).formatted(Formatting.YELLOW))
                .append(Text.literal(" • jail: " + r.jailName()))
                .append(Text.literal(" • until: " + r.expiresAt())),
                false);
        }
        return 1;
    }

    /* ========================= Helpers ========================= */

    private static UUID actorUuid(ServerCommandSource src) {
        return (src.getEntity() instanceof ServerPlayerEntity sp) ? sp.getUuid() : new UUID(0, 0);
    }

    private static String nameOf(ServerCommandSource src, UUID uuid) {
        var sp = src.getServer().getPlayerManager().getPlayer(uuid);
        if (sp != null) return sp.getName().getString();
        try { return Essence.getPlayerStore().get(uuid).name(); }
        catch (Exception e) { return uuid.toString(); }
    }
}
