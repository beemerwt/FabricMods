package com.github.beemerwt.mcrpg.data;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStore {
    static PlayerStore create() { return new SqlitePlayerStore(); }
    void ensurePlayerRow(UUID id, @Nullable String name);
    @NotNull PlayerData get(ServerPlayerEntity player);
    @NotNull PlayerData get(UUID id);

    Optional<PlayerData> lookup(String name);

    List<PlayerData> list();

    /** Paginator helpers */
    int countByPrefix(String prefix);
    List<PlayerData> listByPrefix(String prefix, int offset, int limit);

    void save(PlayerData data);
    void saveAll();
}


