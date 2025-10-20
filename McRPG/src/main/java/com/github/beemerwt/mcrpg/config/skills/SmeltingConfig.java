package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;

/**
 * Now let's make smelting. It's a partner skill of mining and repair, as in the player's smelting level should be the average of the two. It will increase the efficiency of fuel and have a chance to double the output, scaling with level.
 */

@JanksonObject
public class SmeltingConfig extends SkillConfig {

    @JankKey("Second Smelt")
    public DoubleDropsConfig doubleDrops = new DoubleDropsConfig();

    @JankComment("""
        Smelting doesn't directly gain XP, but rewards Mining and Repair with XP.
        The higher your Smelting level, the more XP you gain towards those skills
        when you smelt items.
        """)
    public float baseXpMultiplier = 1.0f;
    public float maxXpMultiplier = 5.0f;

    @JankComment("Fuel efficiency multiplier, scaling with level")
    public float baseFuelEfficiencyMultiplier = 1.0f;
    public float maxFuelEfficiencyMultiplier = 4.0f;

    @JankComment("Smelting XP values (Based on the INPUT item)")
    public Map<String, Integer> items = Map.ofEntries(
        Map.entry("minecraft:raw_copper", 75),
        Map.entry("minecraft:deepslate_redstone_ore", 30),
        Map.entry("minecraft:deepslate_copper_ore", 100),
        Map.entry("minecraft:deepslate_coal_ore", 20),
        Map.entry("minecraft:deepslate_diamond_ore", 140),
        Map.entry("minecraft:deepslate_emerald_ore", 110),
        Map.entry("minecraft:deepslate_iron_ore", 40),
        Map.entry("minecraft:deepslate_gold_ore", 50),
        Map.entry("minecraft:deepslate_lapis_lazuli_ore", 60),
        Map.entry("minecraft:copper_ore", 75),
        Map.entry("minecraft:ancient_debris", 200),
        Map.entry("minecraft:coal_ore", 10),
        Map.entry("minecraft:diamond_ore", 75),
        Map.entry("minecraft:emerald_ore", 100),
        Map.entry("minecraft:gold_ore", 35),
        Map.entry("minecraft:raw_gold", 35),
        Map.entry("minecraft:iron_ore", 25),
        Map.entry("minecraft:raw_iron", 25),
        Map.entry("minecraft:lapis_lazuli_ore", 40),
        Map.entry("minecraft:lapis_ore", 40),
        Map.entry("minecraft:nether_quartz_ore", 25),
        Map.entry("minecraft:redstone_ore", 15),
        Map.entry("minecraft:nether_gold_ore", 35),
        Map.entry("minecraft:cobbled_deepslate", 5)
    );

    public SmeltingConfig() {
        super(SkillType.SMELTING);
        bossbarColor = "PURPLE";
    }


}
