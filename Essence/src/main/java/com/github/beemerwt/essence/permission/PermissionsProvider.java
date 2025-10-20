package com.github.beemerwt.essence.permission;

import net.minecraft.server.command.ServerCommandSource;

public interface PermissionsProvider {
    boolean check(ServerCommandSource src, String node, int opLevelFallback);
    boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms);
}
