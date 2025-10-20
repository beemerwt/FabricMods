package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

@JanksonObject
public class LeafBlowerConfig extends AbilityConfig {
    @JankComment("Minimum level required to use this ability")
    public int minLevel = 100;

    @JankComment("Percentage drop chance of saplings when using Leaf Blower")
    public double saplingDropChance = 10.0;
}
