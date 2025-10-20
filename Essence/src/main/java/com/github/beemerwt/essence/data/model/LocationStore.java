package com.github.beemerwt.essence.data.model;

import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.data.SqliteLocationStore;
import com.github.beemerwt.essence.data.StoredLocation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LocationStore {
    static LocationStore create() { return new SqliteLocationStore(); }

    // Write
    boolean set(UUID playerId, LocationType type, String key, StoredLocation loc);
    default boolean setSingle(UUID playerId, LocationType type, StoredLocation loc) {
        return set(playerId, type, "_", loc); // "_" reserved as the singleton key
    }

    // Read single
    Optional<StoredLocation> get(UUID playerId, LocationType type, String key);
    default Optional<StoredLocation> getSingle(UUID playerId, LocationType type) {
        return get(playerId, type, "_");
    }

    // Read collections
    Map<String, StoredLocation> list(UUID playerId, LocationType type);
    Map<LocationType, Map<String, StoredLocation>> listAll(UUID playerId);

    // Delete
    boolean delete(UUID playerId, LocationType type, String key);
    default boolean deleteSingle(UUID playerId, LocationType type) {
        return delete(playerId, type, "_");
    }

    // Optional cleanup
    boolean deleteAllOfType(UUID playerId, LocationType type);
}
