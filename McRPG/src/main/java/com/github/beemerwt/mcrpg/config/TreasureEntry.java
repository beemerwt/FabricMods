package com.github.beemerwt.mcrpg.config;

import com.github.beemerwt.mcrpg.annotation.JanksonObject;

import java.util.ArrayList;
import java.util.List;

@JanksonObject
public class TreasureEntry {
    public int amount = 1;
    public long xp = 0;
    public double dropChance = 1.0;
    public int levelRequirement = 0;
    public List<String> dropsFrom = new ArrayList<>();

    public TreasureEntry() {}

    public TreasureEntry(int amount, long xp, double dropChance, int levelRequirement, List<String> dropsFrom) {
        this.amount = amount;
        this.xp = xp;
        this.dropChance = dropChance;
        this.levelRequirement = levelRequirement;
        this.dropsFrom.addAll(dropsFrom);
    }
}

