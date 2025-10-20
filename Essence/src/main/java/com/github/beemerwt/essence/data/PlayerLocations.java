package com.github.beemerwt.essence.data;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

// -------- PlayerLocations wrapper --------
final class PlayerLocations {
    private final EnumMap<LocationType, LinkedHashMap<String, StoredLocation>> byType =
            new EnumMap<>(LocationType.class);

    Map<String, StoredLocation> ensureType(LocationType type) {
        return byType.computeIfAbsent(type, t -> new LinkedHashMap<>());
    }

    void put(LocationType type, String key, StoredLocation loc) {
        ensureType(type).put(key, loc);
    }

    StoredLocation get(LocationType type, String key) {
        var m = byType.get(type);
        return (m != null) ? m.get(key) : null;
    }

    void remove(LocationType type, String key) {
        var m = byType.get(type);
        if (m != null) m.remove(key);
    }

    Map<String, StoredLocation> of(LocationType type) {
        var m = byType.get(type);
        return (m != null) ? new LinkedHashMap<>(m) : Map.of();
    }

    Map<LocationType, Map<String, StoredLocation>> snapshot() {
        var copy = new EnumMap<LocationType, Map<String, StoredLocation>>(LocationType.class);
        for (var e : byType.entrySet()) {
            copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        return copy;
    }
}
