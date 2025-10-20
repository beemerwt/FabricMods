package com.github.beemerwt.mcrpg.data;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerData {
    public final UUID id;
    String name;

    // Package-private so we handle through Leveling class only
    final Map<SkillType, Long> xp;

    volatile boolean dirty = false;

    public PlayerData(UUID id, @Nullable String name, Map<SkillType, Long> xp) {
        this.id = Objects.requireNonNull(id, "PlayerData uuid");
        this.name = name;
        this.xp = new EnumMap<>(Objects.requireNonNull(xp, "PlayerData xp"));
    }

    public PlayerData(UUID id, @Nullable String name) {
        this(id, name, new EnumMap<>(SkillType.class));
    }

    public PlayerData(UUID id) {
        this(id, null, new EnumMap<>(SkillType.class));
    }

    void setName(String name) {
        if (!this.name.equals(name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public String getName() {
        if (name.isEmpty()) return id.toString();
        return name;
    }
}

