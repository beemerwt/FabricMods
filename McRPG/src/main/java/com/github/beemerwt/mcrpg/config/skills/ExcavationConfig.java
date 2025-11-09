package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.*;
import com.github.beemerwt.mcrpg.config.ability.GigaDrillBreakerConfig;
import com.github.beemerwt.mcrpg.config.ability.TreasureFindingConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;

@JanksonObject
public class ExcavationConfig extends SkillConfig implements IBlockConfig {
    @JankKey("Giga Drill Breaker")
    public GigaDrillBreakerConfig gigaDrillBreaker = new GigaDrillBreakerConfig();

    @JankComment("Block XP values")
    public Map<String, Integer> blocks = Map.ofEntries(
        Map.entry("minecraft:clay", 40),
        Map.entry("minecraft:dirt", 40),
        Map.entry("minecraft:rooted_dirt", 60),
        Map.entry("minecraft:coarse_dirt", 40),
        Map.entry("minecraft:podzol", 40),
        Map.entry("minecraft:grass_block", 40),
        Map.entry("minecraft:gravel", 40),
        Map.entry("minecraft:mycelium", 40),
        Map.entry("minecraft:sand", 40),
        Map.entry("minecraft:red_sand", 40),
        Map.entry("minecraft:snow", 20),
        Map.entry("minecraft:snow_block", 40),
        Map.entry("minecraft:soul_sand", 40),
        Map.entry("minecraft:soul_soil", 40),
        Map.entry("minecraft:mud", 80),
        Map.entry("minecraft:muddy_mangrove_roots", 90)
    );

    @JankKey("Treasures")
    public TreasureFindingConfig treasureFinding = new TreasureFindingConfig();

    public ExcavationConfig() {
        super(SkillType.EXCAVATION);
        bossbarColor = "YELLOW"; // default
    }

    @Override
    public Map<String, Integer> getBlocks() {
        return blocks;
    }
}
