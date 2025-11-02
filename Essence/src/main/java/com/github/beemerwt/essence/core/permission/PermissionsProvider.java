package com.github.beemerwt.essence.core.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PermissionsProvider {
    boolean check(ServerCommandSource src, String node, int opLevelFallback);
    boolean check(ServerPlayerEntity player, String node, int opLevelFallback);
    boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms);

    /** Return true if the source has ANY granted permission with this prefix, e.g. "essence.summon." */
    default boolean hasAnyChild(ServerCommandSource src, String prefix, int opLevelFallback) {
        // Default: if you can’t enumerate nodes (vanilla), fall back to super-perm via op-level
        // i.e., no children are discoverable; keep it conservative.
        return check(src, prefix.substring(0, Math.max(0, prefix.length()-1)), opLevelFallback);
    }

    default boolean hasAnyChild(ServerPlayerEntity player, String prefix, int opLevelFallback) {
        return hasAnyChild(player.getCommandSource(), prefix, opLevelFallback);
    }
}
