package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankOptional;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;

@JanksonObject
public class SalvageConfig extends SkillConfig {

    @JankComment("Chance to extract a random enchantment as an enchanted book, scaling with level (percent).")
    public float baseExtractChance = 5.0f;
    public float maxExtractChance = 100.0f;

    @JankComment("Level-scaled return multiplier for base materials (percent of the item's baseline craft cost).")
    public float baseReturnPercent = 25.0f;   // e.g., 25% at level 0
    public float maxReturnPercent  = 100.0f;  // up to 100% at high level

    @JankComment("Minimum remaining durability percent required to allow salvaging (integrity gate).")
    public float minIntegrityPercent = 15.0f;

    @JankComment("""
        XP modifiers for different material categories.
        'Base' is base XP per repair; others are multipliers.
        """)
    public Map<String, Float> xpModifiers = Map.ofEntries(
            Map.entry("Base", 1000.0f),
            Map.entry("Wood", 0.6f),
            Map.entry("Stone", 1.3f),
            Map.entry("Iron", 2.5f),
            Map.entry("Gold", 0.3f),
            Map.entry("Diamond", 5.0f),
            Map.entry("Netherite", 6.0f),
            Map.entry("Leather", 1.6f),
            Map.entry("String", 1.8f),
            Map.entry("Other", 1.5f)
    );

    @JanksonObject
    public static class Salvageable {

        @JankOptional
        public String salvageMaterial = null; // e.g. "minecraft:iron_ingot"

        @JankOptional
        public Float minIntegrityPercent = null;

        public int minimumLevel = 0;

        @JankOptional
        public Integer maximumQuantity = null;

        public double xpMultiplier = 1.0;
    }

    @JankComment("""
        Map of item IDs to salvageable data. Any items not included in this list will not be salvageable.
        Fields:
            - salvageMaterial: The material given when salvaged (if null, returns base materials (i.e. Iron Pickaxe gives iron back)).
            - minIntegrityPercent: If set, overrides global minIntegrityPercent for this item.
            - minimumLevel: Minimum Salvage level required to attempt salvaging this item.
            - maximumQuantity: If set, caps the maximum quantity of salvageMaterial returned.
            - xpMultiplier: Multiplier for XP awarded when salvaging this item.
        """)
    public Map<String, Salvageable> salvageables = Map.ofEntries(
            // Wooden tools
            Map.entry("minecraft:wooden_sword",   s(null, null, 0, 2, 0.25f)),
            Map.entry("minecraft:wooden_shovel",  s(null, null, 0, 1, 0.16f)),
            Map.entry("minecraft:wooden_pickaxe", s(null, null, 0, 3, 0.50f)),
            Map.entry("minecraft:wooden_axe",     s(null, null, 0, 3, 0.50f)),
            Map.entry("minecraft:wooden_hoe",     s(null, null, 0, 2, 0.25f)),

            // Stone tools
            Map.entry("minecraft:stone_sword",   s(null, null, 0, 2, 0.25f)),
            Map.entry("minecraft:stone_shovel",  s(null, null, 0, 1, 0.16f)),
            Map.entry("minecraft:stone_pickaxe", s(null, null, 0, 3, 0.50f)),
            Map.entry("minecraft:stone_axe",     s(null, null, 0, 3, 0.50f)),
            Map.entry("minecraft:stone_hoe",     s(null, null, 0, 2, 0.25f)),

            // Iron tools/other
            Map.entry("minecraft:iron_sword",       s(null, null, 0, 2, 0.50f)),
            Map.entry("minecraft:iron_shovel",      s(null, null, 0, 1, 0.30f)),
            Map.entry("minecraft:iron_pickaxe",     s(null, null, 0, 3, 1.00f)),
            Map.entry("minecraft:iron_axe",         s(null, null, 0, 3, 1.00f)),
            Map.entry("minecraft:iron_hoe",         s(null, null, 0, 2, 0.50f)),
            Map.entry("minecraft:shears",           s(null, null, 0, null, 0.50f)),
            Map.entry("minecraft:flint_and_steel",  s(null, null, 0, null, 0.30f)),

            // Iron armor
            Map.entry("minecraft:iron_helmet",     s(null, null, 0, 5, 2.0f)),
            Map.entry("minecraft:iron_chestplate", s(null, null, 0, 8, 2.0f)),
            Map.entry("minecraft:iron_leggings",   s(null, null, 0, 7, 2.0f)),
            Map.entry("minecraft:iron_boots",      s(null, null, 0, 4, 2.0f)),

            // Golden tools
            Map.entry("minecraft:golden_sword",   s(null, null, 0, 2, 4.0f)),
            Map.entry("minecraft:golden_shovel",  s(null, null, 0, 1, 2.6f)),
            Map.entry("minecraft:golden_pickaxe", s(null, null, 0, 3, 8.0f)),
            Map.entry("minecraft:golden_axe",     s(null, null, 0, 3, 8.0f)),
            Map.entry("minecraft:golden_hoe",     s(null, null, 0, 2, 4.0f)),

            // Golden armor
            Map.entry("minecraft:golden_helmet",     s(null, null, 0, 5, 4.0f)),
            Map.entry("minecraft:golden_chestplate", s(null, null, 0, 8, 4.0f)),
            Map.entry("minecraft:golden_leggings",   s(null, null, 0, 7, 4.0f)),
            Map.entry("minecraft:golden_boots",      s(null, null, 0, 4, 4.0f)),

            // Diamond tools
            Map.entry("minecraft:diamond_sword",   s(null, null, 50, 2, 0.50f)),
            Map.entry("minecraft:diamond_shovel",  s(null, null, 50, 1, 0.30f)),
            Map.entry("minecraft:diamond_pickaxe", s(null, null, 50, 3, 1.00f)),
            Map.entry("minecraft:diamond_axe",     s(null, null, 50, 3, 1.00f)),
            Map.entry("minecraft:diamond_hoe",     s(null, null, 50, 2, 0.50f)),

            // Diamond armor
            Map.entry("minecraft:diamond_helmet",     s(null, null, 50, 5, 6.0f)),
            Map.entry("minecraft:diamond_chestplate", s(null, null, 50, 8, 6.0f)),
            Map.entry("minecraft:diamond_leggings",   s(null, null, 50, 7, 6.0f)),
            Map.entry("minecraft:diamond_boots",      s(null, null, 50, 4, 6.0f)),

            // Netherite tools
            Map.entry("minecraft:netherite_sword",   s(null, null, 100, 4, 0.50f)),
            Map.entry("minecraft:netherite_shovel",  s(null, null, 100, 4, 0.30f)),
            Map.entry("minecraft:netherite_pickaxe", s(null, null, 100, 4, 1.00f)),
            Map.entry("minecraft:netherite_axe",     s(null, null, 100, 4, 1.00f)),
            Map.entry("minecraft:netherite_hoe",     s(null, null, 100, 4, 0.50f)),

            // Netherite armor
            Map.entry("minecraft:netherite_helmet",     s(null, null, 100, 4, 6.0f)),
            Map.entry("minecraft:netherite_chestplate", s(null, null, 100, 4, 6.0f)),
            Map.entry("minecraft:netherite_leggings",   s(null, null, 100, 4, 6.0f)),
            Map.entry("minecraft:netherite_boots",      s(null, null, 100, 4, 6.0f)),

            // Leather armor
            Map.entry("minecraft:leather_helmet",     s(null, null, 0, 5, 1.0f)),
            Map.entry("minecraft:leather_chestplate", s(null, null, 0, 8, 1.0f)),
            Map.entry("minecraft:leather_leggings",   s(null, null, 0, 7, 1.0f)),
            Map.entry("minecraft:leather_boots",      s(null, null, 0, 4, 1.0f)),

            // String-based gear
            Map.entry("minecraft:fishing_rod",        s(null, null, 0, null, 0.50f)),
            Map.entry("minecraft:bow",                s(null, null, 0, null, 0.50f)),
            Map.entry("minecraft:carrot_on_a_stick",  s(null, null, 0, null, 0.50f)),
            Map.entry("minecraft:crossbow",           s(null, null, 0, null, 1.00f)),
            Map.entry("minecraft:trident",            s(null, null, 0, null, 1.00f))
    );

    private static Salvageable s(String mat, Float minDur, int minLvl, Integer maxQty, Float xpMul) {
        Salvageable ss = new Salvageable();
        ss.salvageMaterial = mat;
        ss.minIntegrityPercent = minDur;
        ss.minimumLevel = minLvl;
        ss.maximumQuantity = maxQty;
        ss.xpMultiplier = xpMul;
        return ss;
    }

    public SalvageConfig() {
        super(SkillType.SALVAGE);
        bossbarColor = "PURPLE";
    }
}
