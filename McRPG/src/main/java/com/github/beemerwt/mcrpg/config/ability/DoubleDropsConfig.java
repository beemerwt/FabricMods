package com.github.beemerwt.mcrpg.config.ability;


import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

import java.util.List;

@JanksonObject
public class DoubleDropsConfig extends AbilityConfig {
    @JankComment("Percentage chance to trigger, scaling with skill level")
    public float baseChance = 0.0f;
    public float maxChance = 100.0f;
}
