package com.github.beemerwt.essence.core.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class VanillaPermissionsProvider implements PermissionsProvider{
    @Override
    public boolean check(ServerCommandSource src, String node, int opLevelFallback) {
        return src.hasPermissionLevel(opLevelFallback);
    }

    @Override
    public boolean check(ServerPlayerEntity player, String node, int opLevelFallback) {
        return player.hasPermissionLevel(opLevelFallback);
    }

    @Override
    public boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        return defIfNoPerms;
    }

    @Override
    public boolean hasAnyChild(ServerCommandSource src, String prefix, int opLevelFallback) {
        // No way to inspect children; treat as "supernode or op".
        return src.hasPermissionLevel(opLevelFallback);
    }
}
