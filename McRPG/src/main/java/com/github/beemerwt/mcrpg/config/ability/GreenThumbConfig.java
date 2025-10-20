package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

@JanksonObject
public class GreenThumbConfig extends AbilityConfig {
    @JankComment("Growth speed multiplier, scaling with skill level")
    public float baseGrowthMultiplier = 1.0f;
    public float maxGrowthMultiplier = 4.0f;

}
