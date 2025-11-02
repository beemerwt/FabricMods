package com.github.beemerwt.essence.core.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public record DeferredCommand(ServerCommandSource src,
                              MinecraftServer server,
                              ServerWorld world,
                              ServerPlayerEntity player) {
    public static DeferredCommand defer(ServerCommandSource source) {
        return new DeferredCommand(
            source,
            source.getServer(),
            source.getWorld(),
            source.getPlayer()
        );
    }
}
