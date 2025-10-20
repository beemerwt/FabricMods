package com.github.beemerwt.fakeplayer;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public final class FakePlayerConfig {
    @SerializedName("players")
    public List<Entry> players = new ArrayList<>();

    public static final class Entry {
        public String name;
        public String dimension; // e.g. "minecraft:overworld"
        public double x, y, z;
        public float yaw, pitch;
        public boolean autoSpawn = true;

        public Entry() {}

        public Entry(String name, String dimension, double x, double y, double z, float yaw, float pitch, boolean autoSpawn) {
            this.name = name;
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
            this.autoSpawn = autoSpawn;
        }
    }
}

