package com.github.beemerwt.essence.core.command.suggest.backend;

import com.github.beemerwt.essence.core.Essence;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

public class BannedBackend implements Backend {
    @Override
    public int count(MinecraftServer server, String prefix) {
        return Essence.getSuspensionStore().countBansByPrefix(prefix);
    }

    @Override
    public void streamNames(MinecraftServer server, String prefix, int offset, int limit, Consumer<String> sink) {
        Essence.getSuspensionStore().listBansByPrefix(prefix, offset, limit).forEach(sink);
    }
}
