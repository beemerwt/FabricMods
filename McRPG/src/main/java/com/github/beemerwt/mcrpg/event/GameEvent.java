package com.github.beemerwt.mcrpg.event;

import net.minecraft.server.network.ServerPlayerEntity;

// Generic normalized events:
public interface GameEvent {
    ServerPlayerEntity player();
}
