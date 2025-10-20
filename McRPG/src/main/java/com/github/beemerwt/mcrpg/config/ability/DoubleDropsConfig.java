package com.github.beemerwt.mcrpg.config.ability;


import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;

import java.util.List;

@JanksonObject
public class DoubleDropsConfig {
    @JankComment("Set to false to disable this ability")
    public boolean enabled = true;

    @JankComment("Percentage chance to trigger, scaling with skill level")
    public float baseChance = 0.0f;
    public float maxChance = 100.0f;

    @JankComment("List of blocks that can drop double items when mined")
    public List<String> enabledFor = List.of(
        "minecraft:coal_ore",
        "minecraft:iron_ore",
        "minecraft:gold_ore",
        "minecraft:copper_ore",
        "minecraft:redstone_ore",
        "minecraft:lapis_ore",
        "minecraft:diamond_ore",
        "minecraft:emerald_ore",
        "minecraft:nether_gold_ore",
        "minecraft:nether_quartz_ore"
    );
}
