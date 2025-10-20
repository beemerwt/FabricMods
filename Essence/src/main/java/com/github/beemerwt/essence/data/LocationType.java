package com.github.beemerwt.essence.data;

public enum LocationType {
    BACK(0),
    SPAWN(1), // Used for player spawns and global spawn points
    WARP(2),
    HOME(3);

    private final int id;
    LocationType(int id) {
        this.id = id;
    }
}
