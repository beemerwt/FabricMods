package com.github.beemerwt.essence.core.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/** Fabric Permissions API v0 wrapper (auto-bridges to LuckPerms when LP is installed). */
public final class FabricPermissionsProvider implements PermissionsProvider {
    private final Method checkWithLevel;
    private final Method checkWithDefault;
    private final Method playerCheckWithLevel;

    FabricPermissionsProvider() {
        try {
            Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            checkWithLevel = permsClass.getMethod("check", ServerCommandSource.class, String.class, int.class);
            checkWithDefault = permsClass.getMethod("check", ServerCommandSource.class, String.class, boolean.class);
            playerCheckWithLevel = permsClass.getMethod("check", ServerPlayerEntity.class, String.class, int.class);
        } catch (Exception e) {
            throw new IllegalStateException("FabricPermissionsProvider init failure", e);
        }
    }

    // ----- core checks -----

    @Override
    public boolean check(ServerCommandSource src, String node, int opLevelFallback) {
        try {
            return (Boolean) checkWithLevel.invoke(null, src, node, opLevelFallback);
        } catch (Throwable t) {
            // Safety net: if the API errors, fall back to vanilla
            return src.hasPermissionLevel(opLevelFallback);
        }
    }

    @Override
    public boolean check(ServerPlayerEntity player, String node, int opLevelFallback) {
        try {
            return (Boolean) playerCheckWithLevel.invoke(null, player, node, opLevelFallback);
        } catch (Throwable t) {
            // Safety net: if the API errors, fall back to vanilla
            return player.hasPermissionLevel(opLevelFallback);
        }
    }

    @Override
    public boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        try {
            return (Boolean) checkWithDefault.invoke(null, src, node, defIfNoPerms);
        } catch (Throwable t) {
            return defIfNoPerms;
        }
    }

    /** Returns true if the subject has ANY child permission with this prefix, e.g. "essence.summon." → checks "essence.summon.*". */
    @Override
    public boolean hasAnyChild(ServerCommandSource src, String prefix, int opLevelFallback) {
        final String wildcard = prefix.endsWith(".") ? (prefix + "*") : (prefix + ".*");
        try {
            // When LP is installed, Fabric Perms bridges this to LP and honors wildcards.
            return (Boolean) checkWithLevel.invoke(null, src, wildcard, opLevelFallback);
        } catch (Throwable t) {
            return src.hasPermissionLevel(opLevelFallback);
        }
    }

    @Override
    public boolean hasAnyChild(ServerPlayerEntity player, String prefix, int opLevelFallback) {
        final String wildcard = prefix.endsWith(".") ? (prefix + "*") : (prefix + ".*");
        try {
            return (Boolean) playerCheckWithLevel.invoke(null, player, wildcard, opLevelFallback);
        } catch (Throwable t) {
            return player.hasPermissionLevel(opLevelFallback);
        }
    }
}
