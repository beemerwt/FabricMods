package com.github.beemerwt.essence.permission;

import net.minecraft.server.command.ServerCommandSource;

/** Fabric Permissions API v0 wrapper (auto-bridges to LuckPerms when LP is installed). */
public final class FabricPermissionsProvider implements PermissionsProvider {
    private final java.lang.reflect.Method checkWithLevel;
    private final java.lang.reflect.Method checkWithDefault;

    FabricPermissionsProvider() {
        try {
            Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            checkWithLevel = permsClass.getMethod("check", ServerCommandSource.class, String.class, int.class);
            checkWithDefault = permsClass.getMethod("check", ServerCommandSource.class, String.class, boolean.class);
        } catch (Exception e) {
            throw new IllegalStateException("FabricPermissionsProvider init failure", e);
        }
    }

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
    public boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        try {
            return (Boolean) checkWithDefault.invoke(null, src, node, defIfNoPerms);
        } catch (Throwable t) {
            return defIfNoPerms;
        }
    }
}
