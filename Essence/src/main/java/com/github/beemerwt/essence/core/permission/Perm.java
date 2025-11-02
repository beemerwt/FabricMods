package com.github.beemerwt.essence.core.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.function.Predicate;

public final class Perm implements Predicate<ServerCommandSource> {
    private final String node;
    private final OpLevel fallback;

    private Perm(String node, OpLevel fallback) {
        this.node = Objects.requireNonNull(node, "node");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    public static Perm of(String node, OpLevel fallback) {
        return new Perm(node, fallback);
    }

    /** Useful for hierarchical nodes, e.g. essence.ban -> essence.ban.time */
    public Perm child(String suffix) {
        return new Perm(node + "." + suffix, fallback);
    }

    public Predicate<ServerCommandSource> orChild(String suffix) {
        return this.or(new Perm(node + "." + suffix, fallback));
    }

    public Predicate<ServerCommandSource> orChildren(String... suffixes) {
        Predicate<ServerCommandSource> combined = this;
        for (String suffix : suffixes) {
            combined = combined.or(new Perm(node + "." + suffix, fallback));
        }

        return combined;
    }

    /** Brigadier requires(...) support (supernode OR any child). */
    public Predicate<ServerCommandSource> any() {
        final String prefix = node + ".";
        return src -> Permissions.check(src, node, fallback) ||
            Permissions.hasAnyChild(src, prefix, fallback);
    }

    /** Execution-time guard: supernode OR the specific child. */
    public boolean allowsChild(ServerCommandSource src, String suffix) {
        return Permissions.check(src, node, fallback) ||
            Permissions.check(src, node + "." + suffix, fallback);
    }

    public String node() { return node; }
    public OpLevel fallback() { return fallback; }

    /** Convenience: Permissions.check(...) under the hood. */
    public boolean check(ServerCommandSource src) {
        return Permissions.check(src, node, fallback);
    }

    public boolean check(ServerPlayerEntity player) {
        return Permissions.check(player, node, fallback);
    }

    /** So this can be passed directly to Brigadier's .requires(...) */
    @Override
    public boolean test(ServerCommandSource src) {
        return check(src);
    }

    /** Throw a nice error if lacking permission. */
    public boolean require(ServerCommandSource src) {
        return check(src);
    }

    @Override
    public String toString() {
        return node + " (fallback " + fallback + ")";
    }
}
