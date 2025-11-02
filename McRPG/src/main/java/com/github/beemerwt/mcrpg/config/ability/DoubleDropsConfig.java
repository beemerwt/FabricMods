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
}
