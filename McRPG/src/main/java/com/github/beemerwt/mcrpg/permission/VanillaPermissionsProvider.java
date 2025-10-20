package com.github.beemerwt.mcrpg.permission;

import net.minecraft.server.command.ServerCommandSource;

public class VanillaPermissionsProvider implements PermissionsProvider{
    @Override
    public boolean check(ServerCommandSource src, String node, int opLevelFallback) {
        return src.hasPermissionLevel(opLevelFallback);
    }
    @Override
    public boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        return defIfNoPerms;
    }
}
