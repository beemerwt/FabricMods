package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.IHasBlocks;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.ability.BlastMiningConfig;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.config.ability.SuperBreakerConfig;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;
import java.util.Optional;

@JanksonObject
public class MiningConfig extends SkillConfig implements IHasBlocks {
    @JankKey("Double Drops")
    public DoubleDropsConfig doubleDrops = new DoubleDropsConfig();

    @JankKey("Super Breaker")
    public SuperBreakerConfig superBreaker = new SuperBreakerConfig();

    @JankKey("Blast Mining")
    public BlastMiningConfig blastMining = new BlastMiningConfig();

    @JankComment("Block XP values")
    public Map<String, Integer> blocks = Map.ofEntries(
        // --- Stone Family ---
        Map.entry("minecraft:stone", 15),
        Map.entry("minecraft:cobblestone", 15),
        Map.entry("minecraft:mossy_cobblestone", 30),
        Map.entry("minecraft:granite", 15),
        Map.entry("minecraft:diorite", 15),
        Map.entry("minecraft:andesite", 15),
        Map.entry("minecraft:sandstone", 30),
        Map.entry("minecraft:red_sandstone", 100),
        Map.entry("minecraft:obsidian", 150),
        Map.entry("minecraft:end_stone", 15),

        // --- Deepslate Family ---
        Map.entry("minecraft:deepslate", 30),
        Map.entry("minecraft:cobbled_deepslate", 15),
        Map.entry("minecraft:tuff", 10),
        Map.entry("minecraft:dripstone_block", 35),
        Map.entry("minecraft:calcite", 400),
        Map.entry("minecraft:smooth_basalt", 300),

        // --- Ore Family ---
        Map.entry("minecraft:coal_ore", 400),
        Map.entry("minecraft:iron_ore", 900),
        Map.entry("minecraft:gold_ore", 1300),
        Map.entry("minecraft:copper_ore", 1400),
        Map.entry("minecraft:redstone_ore", 600),
        Map.entry("minecraft:lapis_ore", 800),
        Map.entry("minecraft:diamond_ore", 2400),
        Map.entry("minecraft:emerald_ore", 1000),

        // --- Deepslate Ore Family ---
        Map.entry("minecraft:deepslate_coal_ore", 700),
        Map.entry("minecraft:deepslate_iron_ore", 1300),
        Map.entry("minecraft:deepslate_gold_ore", 1900),
        Map.entry("minecraft:deepslate_copper_ore", 1900),
        Map.entry("minecraft:deepslate_redstone_ore", 900),
        Map.entry("minecraft:deepslate_lapis_ore", 1400),
        Map.entry("minecraft:deepslate_diamond_ore", 3600),
        Map.entry("minecraft:deepslate_emerald_ore", 1700),

        // --- Nether Ores and Blocks ---
        Map.entry("minecraft:nether_quartz_ore", 300),
        Map.entry("minecraft:nether_gold_ore", 1300),
        Map.entry("minecraft:ancient_debris", 7777),
        Map.entry("minecraft:netherrack", 15),
        Map.entry("minecraft:blackstone", 55),
        Map.entry("minecraft:gilded_blackstone", 200),
        Map.entry("minecraft:basalt", 40),
        Map.entry("minecraft:magma_block", 30),
        Map.entry("minecraft:crying_obsidian", 3000),

        // --- Amethyst Family ---
        Map.entry("minecraft:amethyst_block", 500),
        Map.entry("minecraft:budding_amethyst", 400),
        Map.entry("minecraft:small_amethyst_bud", 10),
        Map.entry("minecraft:medium_amethyst_bud", 20),
        Map.entry("minecraft:large_amethyst_bud", 30),
        Map.entry("minecraft:amethyst_cluster", 60),

        // --- Miscellaneous Natural Blocks ---
        Map.entry("minecraft:bone_block", 150),
        Map.entry("minecraft:glowstone", 15),
        Map.entry("minecraft:packed_ice", 15),
        Map.entry("minecraft:blue_ice", 45)
    );

    public MiningConfig() {
        super(SkillType.MINING);
        bossbarColor = "YELLOW"; // default
    }

    @Override
    public boolean hasAbility(ActiveAbilityType activeAbilityType) {
        return activeAbilityType == ActiveAbilityType.SUPER_BREAKER
            || activeAbilityType == ActiveAbilityType.BLAST_MINING;
    }

    @Override
    public Optional<AbilityConfig> getAbilityConfig(ActiveAbilityType activeAbilityType) {
        return switch (activeAbilityType) {
            case SUPER_BREAKER -> Optional.of(superBreaker);
            case BLAST_MINING -> Optional.of(blastMining);
            default -> Optional.empty();
        };
    }

    @Override
    public Map<String, Integer> getBlocks() {
        return blocks;
    }
}
