package com.github.beemerwt.mcrpg.config.ability;


import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;

@JanksonObject
public class GigaDrillBreakerConfig extends SuperAbilityConfig {
    @JankComment("Digging speed multiplier, scaling with skill level")
    public float baseSpeedMultipier = 1.5f;
    public float maxSpeedMultipier = 4.0f;
}
