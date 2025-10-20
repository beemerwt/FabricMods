package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

@JanksonObject
public class AcrobaticsConfig extends SkillConfig {
    @JanksonObject
    public static class Dodge {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("XP awarded for a successful dodge")
        public int xpAwarded = 800;

        @JankComment("Chance of activation, scaling with skill level")
        public int baseChance = 5;
        public int maxChance = 35;

        @JankComment("Cooldown in seconds, scaling with level")
        public int baseCooldown = 10;
        public int minCooldown = 5;

        @JankComment("Damage taken will be divided by this value")
        public double baseDamageModifier = 2.0;
    }

    @JanksonObject
    public static class Roll {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("XP awarded for a successful roll")
        public int xpAwarded = 600;

        @JankComment("Chance of activation, scaling with level")
        public int baseChance = 1;
        public int maxChance = 100;

        @JankComment("Cooldown in seconds, scaling with level")
        public int baseCooldown = 10;
        public int maxCooldown = 3;

        @JankComment("The flat damage reduction, scaling with level")
        public float baseDamageReduction = 10;
        public float maxDamageReduction = 50;
    }

    @JankKey("Roll")
    public Roll roll = new Roll();

    @JankKey("Dodge")
    public Dodge dodge = new Dodge();

    @JankComment("XP awarded for taking fall damage")
    public int fallXp = 600;

    @JankComment("Multiply XP by this value when wearing the Feather Fall enchant")
    public double featherFallMultiplier = 2.0;

    public AcrobaticsConfig() {
        super(SkillType.ACROBATICS);
        bossbarColor = "PINK"; // default
    }
}
