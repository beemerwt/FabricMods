package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;

import java.util.Map;

@JanksonObject
public class GreenTerraConfig extends SuperAbilityConfig {

    @JankComment("The percentage chance to replant a crop when harvested, scaling with skill level")
    public float baseReplantChance = 20.0f;
    public float maxReplantChance = 100.0f;

    // Green Terra is the ability to replant the crops automatically
    @JankComment("Applicable crops")
    public Map<String, Boolean> applicableCrops = Map.ofEntries(
            Map.entry("minecraft:wheat", true),
            Map.entry("minecraft:carrots", true),
            Map.entry("minecraft:potatoes", true),
            Map.entry("minecraft:beetroots", true),
            Map.entry("minecraft:nether_wart", true),
            Map.entry("minecraft:sweet_berries", true),
            Map.entry("minecraft:cocoa", true),
            Map.entry("minecraft:bamboo", true),
            Map.entry("minecraft:melon_stem", true),
            Map.entry("minecraft:pumpkin_stem", true),
            Map.entry("minecraft:sugar_cane", true)
    );
}
