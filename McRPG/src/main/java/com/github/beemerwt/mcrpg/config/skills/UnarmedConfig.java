package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

@JanksonObject
public class UnarmedConfig extends SkillConfig {
    @JanksonObject
    public static class ArrowDeflect {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Chance to deflect an incoming arrow, scaling with skill level")
        public double baseChance = 0.0;
        public double maxChance = 50.0;
    }

    @JanksonObject
    public static class SteelArmStyle {
        @JankComment("Set to false to disable this ability")
        public boolean enabled = true;

        @JankComment("Damage bonus applied when hitting an enemy with a punch, scaling with skill level")
        public double baseDamageBonus = 1.0;
        public double maxDamageBonus = 13.5;

        @JankComment("Radius around the enemy in which others will be hit, scaling with skill level")
        public double baseSweepRadius = 0.0;
        public double maxSweepRadius = 3.0;
    }

    @JankKey("Arrow Deflect")
    public ArrowDeflect arrowDeflect = new ArrowDeflect();

    @JankKey("Steel Arm Style")
    public SteelArmStyle steelArmStyle = new SteelArmStyle();

    public UnarmedConfig() {
        super(SkillType.UNARMED);
        bossbarColor = "BLUE";
    }
}
