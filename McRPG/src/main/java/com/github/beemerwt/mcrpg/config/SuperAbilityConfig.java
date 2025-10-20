package com.github.beemerwt.mcrpg.config;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;

@JanksonObject
public class SuperAbilityConfig extends AbilityConfig {
    @JankComment("Minimum level required to unlock this ability")
    public int minLevel = 50;

    @JankComment("Cooldown time in seconds, scaling with level")
    public int baseCooldown = 240;
    public int minCooldown = 160;

    @JankComment("Duration in seconds, scaling with skill level")
    public int baseDuration = 5;
    public int maxDuration = 30;
}
