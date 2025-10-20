// runtime/SpawnerCapTracker.java
package com.github.beemerwt.spawnertweaks.runtime;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerCapTracker {
    private SpawnerCapTracker() {}

    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();
    private static final String TAG_PREFIX = "spawnertweaks:"; // + key

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            String key = keyFromTags(entity);
            if (key != null) COUNTS.merge(key, 1, Integer::sum);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            String key = keyFromTags(entity);
            if (key != null) COUNTS.computeIfPresent(key, (k, v) -> (v <= 1) ? null : v - 1);
        });
    }

    public static int count(String key) {
        return key == null ? 0 : COUNTS.getOrDefault(key, 0);
    }

    public static String makeTag(String key) {
        return TAG_PREFIX + key;
    }

    private static String keyFromTags(Entity e) {
        for (String t : e.getCommandTags()) {
            if (t.startsWith(TAG_PREFIX)) return t.substring(TAG_PREFIX.length());
        }
        return null;
    }
}
