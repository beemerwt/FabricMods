package com.github.beemerwt.mcrpg.config.ability;


import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

import java.util.ArrayList;
import java.util.List;

@JanksonObject
public class BlastMiningConfig extends AbilityConfig {
    @JankComment("Whether blast mining can yield bonus drops")
    public boolean bonusDrops = true;

    @JankComment("Flat radius bonus for the TNT, scaling with skill level")
    public int baseRadiusModifier = 1;
    public int maxRadiusModifier = 10;

    @JankComment("Percentage of debris reduced, scaling with skill level")
    public int baseDebrisReduction = 10;
    public int maxDebrisReduction = 30;

    @JankComment(value= """
            Chance of extra ores dropped, scaling with skill level
            This will be disabled if bonusDrops is false
            100% means that every ore block will drop extra ores
            """)
    public int baseOreModifier = 35;
    public int maxOreModifier = 70;

    @JankComment("Multiplier for the number of bonus drops, scaling with skill level")
    public double baseDropMultiplier = 1.0;
    public double maxDropMultiplier = 3.0;

    @JankComment("If damage is taken, it will be reduced by this percentage, scaling with skill level")
    public int baseBlastResistance = 0;
    public int maxBlastResistance = 100;

    @JankComment("""
        NOT YET IMPLEMENTED
        Allow or disallow specific blocks from being affected by blast mining.
        By default, all ores can have bonus drops.
        Whitelisted blocks are automatically removed from the debris reduction calculation
        Blacklisted blocks won't be added to the debris reduction calculation
        Use Minecraft block IDs, e.g. minecraft:diamond_ore
        """)
    public List<String> whitelist = new ArrayList<>();
    public List<String> blacklist = new ArrayList<>();
}
