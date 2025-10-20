package com.github.beemerwt.essence.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/** Direct LuckPerms API check (only needed when Fabric Perms API is not present). */
public final class LuckPermissionsProvider implements PermissionsProvider {
    private final Object luckPerms;         // net.luckperms.api.LuckPerms
    private final Method getPlayerAdapter;  // getPlayerAdapter(Class)
    private final Method hasPermission;     // adapter.hasPermission(ServerPlayerEntity, String)

    LuckPermissionsProvider() {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPerms = provider.getMethod("get").invoke(null);

            getPlayerAdapter = luckPerms.getClass()
                    .getMethod("getPlayerAdapter", Class.class);

            // Resolve adapter type reflectively, then cache its hasPermission method
            Object adapter = getPlayerAdapter.invoke(luckPerms, ServerPlayerEntity.class);
            hasPermission = adapter.getClass()
                    .getMethod("hasPermission", ServerPlayerEntity.class, String.class);
        } catch (Exception e) {
            throw new IllegalStateException("LuckPermissionsProvider init failure", e);
        }
    }

    @Override
    public boolean check(ServerCommandSource src, String node, int opLevelFallback) {
        ServerPlayerEntity player = src.getEntity() instanceof ServerPlayerEntity p ? p : null;
        if (player == null) {
            // Console/command blocks: use vanilla threshold
            return src.hasPermissionLevel(opLevelFallback);
        }
        try {
            Object adapter = getPlayerAdapter.invoke(luckPerms, ServerPlayerEntity.class);
            Boolean has = (Boolean) hasPermission.invoke(adapter, player, node);
            return has != null ? has : src.hasPermissionLevel(opLevelFallback);
        } catch (Throwable t) {
            return src.hasPermissionLevel(opLevelFallback);
        }
    }

    @Override
    public boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        ServerPlayerEntity player = src.getEntity() instanceof ServerPlayerEntity p ? p : null;
        if (player == null) return defIfNoPerms;
        try {
            Object adapter = getPlayerAdapter.invoke(luckPerms, ServerPlayerEntity.class);
            Boolean has = (Boolean) hasPermission.invoke(adapter, player, node);
            return has != null ? has : defIfNoPerms;
        } catch (Throwable t) {
            return defIfNoPerms;
        }
    }
}
