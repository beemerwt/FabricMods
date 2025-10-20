package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

@JanksonObject
public class SwordsConfig extends SkillConfig {
    @JanksonObject
    public static class SerratedStrikes {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Damage will get divided by this modifier, scaling from base to max with level")
        public double baseDamageModifier = 4.0;
        public double minDamageModifier = 1.5;

        @JankComment("Bleed duration in ticks, scaling from base to max with level")
        public int baseBleedTicks = 5;
        public int maxBleedTicks = 10;
    }

    @JanksonObject
    public static class CounterAttack {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Maximum percentage chance to counter-attack at max level")
        public double maxChance = 30.0;

        @JankComment("Reflected damage will get divided by this modifier")
        public double damageModifier = 2.0;
    }

    @JanksonObject
    public static class LimitBreak {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Damage bonus applied when hitting an enemy with a sword, scaling with skill level")
        public float baseDamageBonus = 0.0f;
        public float maxDamageBonus = 8.0f;
    }

    @JankKey("Serrated Strikes")
    public SerratedStrikes serratedStrikes = new SerratedStrikes();

    @JankKey("Counter-Attack")
    public CounterAttack counterAttack = new CounterAttack();

    @JankKey("Limit Break")
    public LimitBreak limitBreak = new LimitBreak();

    public SwordsConfig() {
        super(SkillType.SWORDS);
        bossbarColor = "BLUE";
    }
}
