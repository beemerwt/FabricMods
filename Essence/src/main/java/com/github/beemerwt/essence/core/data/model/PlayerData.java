package com.github.beemerwt.essence.core.data.model;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private final String name;
    private boolean noClip;

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public boolean noClip() { return noClip; }

    public void setNoclip(boolean noClip) { this.noClip = noClip; }

    public PlayerData(UUID uuid, String name, boolean noClip) {
        this.uuid = uuid;
        this.name = name;
        this.noClip = noClip;
    }

    public PlayerData(UUID uuid, String name) {
        this(uuid, name, false);
    }
}
