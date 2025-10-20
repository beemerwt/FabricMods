package com.github.beemerwt.essence.data.model;

import com.github.beemerwt.essence.data.PlayerData;
import com.github.beemerwt.essence.data.SqlitePlayerStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStore {
    static PlayerStore create() { return new SqlitePlayerStore(); }
    PlayerData get(ServerPlayerEntity player);
    PlayerData get(UUID id);

    /** Online players (from the hot cache). */
    List<PlayerData> list();

    Optional<PlayerData> lookup(String name);
    List<PlayerData> all();

    /** Count players whose name starts with prefix (case-insensitive). */
    int countByPrefix(String prefix);

    /** Page through players by name prefix (case-insensitive), newest first. */
    List<PlayerData> listByPrefix(String prefix, int offset, int limit);
}



