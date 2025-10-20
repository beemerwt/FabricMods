package com.github.beemerwt.mcrpg.config.ability;


import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;

@JanksonObject
public class SuperBreakerConfig extends SuperAbilityConfig {
    @JankComment("Mining speed multiplier, scaling with skill level")
    public float baseSpeedMultipier = 1.5f; // Mining speed multiplier at level 1
    public float maxSpeedMultipier = 4.0f; // Mining speed multiplier at max level
}
