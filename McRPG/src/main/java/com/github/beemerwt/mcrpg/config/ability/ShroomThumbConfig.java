package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.config.AbilityConfig;

/**
 * Using a mushroom on a block of dirt while both a red and brown mushroom are in the inventory triggers "Shroom Thumb".
 * It consumes a red and brown mushroom, and has a chance to turn the block of dirt into a block of mycelium.
 * The chances of success increases with Herbalism level.
 */
public class ShroomThumbConfig extends AbilityConfig {
    @JankComment("The chance to turn a block of dirt into mycelium, scaling with skill level")
    public double baseChance = 5.0;
    public double maxChance = 50.0;
}
