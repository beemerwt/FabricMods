package com.github.beemerwt.essence.core.command.suggest.backend;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.model.PlayerData;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.function.Consumer;

public class AllPlayersBackend implements Backend {
    @Override
    public int count(MinecraftServer server, String prefix) {
        return Essence.getPlayerStore().countByPrefix(prefix);
    }

    @Override
    public void streamNames(MinecraftServer server, String prefix, int offset, int limit, Consumer<String> sink) {
        Essence.getPlayerStore()
            .listByPrefix(prefix, offset, limit)
            .stream()
            .map(PlayerData::name)
            .filter(Objects::nonNull)
            .forEach(sink);
    }
}
