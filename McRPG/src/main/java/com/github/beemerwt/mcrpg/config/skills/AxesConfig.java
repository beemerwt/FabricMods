package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

@JanksonObject
public class AxesConfig extends SkillConfig {

    @JanksonObject
    public static class AxeMastery {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Damage bonus applied when hitting an enemy with an axe, scaling with skill level")
        public double baseDamageBonus = 0.0;
        public double maxDamageBonus = 8.0;
    }

    @JanksonObject
    public static class CriticalStrikes {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Chance to land a critical strike, scaling with skill level")
        public double baseChance = 0.0;
        public double maxChance = 37.5;

        @JankComment("Damage multiplier applied on a critical strike")
        public double pvpDamageMultiplier = 1.5;
        public double pveDamageMultiplier = 2.0;
    }

    @JanksonObject
    public static class GreaterImpact {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Chance to trigger")
        public double baseChance = 25.0;

        @JankComment("Velocity multiplier applied to knockback effects")
        public double knockbackModifier = 1.5;

        @JankComment("Bonus damage applied when triggered")
        public double damageBonus = 2.0;
    }

    @JankKey("Axe Mastery")
    public AxeMastery axeMastery = new AxeMastery();

    @JankKey("Critical Strikes")
    public CriticalStrikes criticalStrikes = new CriticalStrikes();

    @JankKey("Greater Impact")
    public GreaterImpact greaterImpact = new GreaterImpact();

    public AxesConfig() {
        super(SkillType.AXES);
        bossbarColor = "BLUE"; // default
    }
}
