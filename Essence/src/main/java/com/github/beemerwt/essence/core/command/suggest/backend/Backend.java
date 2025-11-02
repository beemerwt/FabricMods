package com.github.beemerwt.essence.core.command.suggest.backend;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/** Minimal abstraction over a name source. */
public interface Backend {
    int count(MinecraftServer server, String prefix);
    void streamNames(MinecraftServer server, String prefix, int offset, int limit, Consumer<String> sink);
}
