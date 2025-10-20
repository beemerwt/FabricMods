package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankOptional;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;

@JanksonObject
public class RepairConfig extends SkillConfig {

    @JankComment("The amount of durability restored per repair action; random between base and max.")
    public float baseDurabilityPercent = 10.0f;
    public float maxDurabilityPercent = 25.0f;

    @JankComment("""
        The percentage amount of bonus durability restored on a successful repair action, scaling with level.
        i.e. 0.0 = no bonus, 100.0 = double the durability restored.
        """)
    public float baseBonusPercentage = 0.0f;
    public float maxBonusPercentage = 200.0f;

    @JankComment("""
        The percentage chance (0-100) to perform a super repair, scaling with level.
        Doubles the amount of durability restored after the base bonus percentage.
        Does not override max durability if specified.
        """)
    public float baseSuperRepairChance = 0.0f;
    public float maxSuperRepairChance = 20.0f;

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

    // ===== mcMMO-like per-item section =====
    @JanksonObject
    public static class Repairable {
        @JankOptional
        public String repairMaterial = null;

        @JankOptional
        public Integer maximumDurability = null;

        public int minimumLevel = 0;

        @JankOptional
        public Integer minimumQuantity = 1;

        public float xpMultiplier = 1.0f;
    }

    @JankComment("""
    Map of item IDs to repairable data. Any items not included in this list will not be repairable.
    - repairMaterial: The item ID of the material used to repair this item. If null or empty, any material may be used.
    - maximumDurability: The maximum durability the item can have to be repairable. If null, no maximum.
    - minimumLevel: The minimum Repair skill level required to repair this item.
    - minimumQuantity: The minimum quantity of the repair material required to perform a repair. If null, defaults to 1.
    - xpMultiplier: Multiplier for XP gained from repairing this item.
    """)
    public Map<String, Repairable> repairables = Map.<String, Repairable>ofEntries(
            // Shield (wood-based)
            Map.entry("minecraft:shield", r("minecraft:oak_planks", 336, 6, 0, 0.25f)),

            // Wooden tools
            Map.entry("minecraft:wooden_sword",   r(null, null, null, 0, 0.25f)),
            Map.entry("minecraft:wooden_shovel",  r(null, null, null, 0, 0.16f)),
            Map.entry("minecraft:wooden_pickaxe", r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:wooden_axe",     r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:wooden_hoe",     r(null, null, null, 0, 0.25f)),

            // Stone tools
            Map.entry("minecraft:stone_sword",   r(null, null, null, 0, 0.25f)),
            Map.entry("minecraft:stone_shovel",  r(null, null, null, 0, 0.16f)),
            Map.entry("minecraft:stone_pickaxe", r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:stone_axe",     r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:stone_hoe",     r(null, null, null, 0, 0.25f)),

            // Iron tools / misc
            Map.entry("minecraft:iron_sword",      r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:iron_shovel",     r(null, null, null, 0, 0.30f)),
            Map.entry("minecraft:iron_pickaxe",    r(null, null, null, 0, 1.00f)),
            Map.entry("minecraft:iron_axe",        r(null, null, null, 0, 1.00f)),
            Map.entry("minecraft:iron_hoe",        r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:shears",          r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:flint_and_steel", r(null, null, null, 0, 0.30f)),

            // Iron armor
            Map.entry("minecraft:iron_helmet",     r(null, null, null, 0, 2.0f)),
            Map.entry("minecraft:iron_chestplate", r(null, null, null, 0, 2.0f)),
            Map.entry("minecraft:iron_leggings",   r(null, null, null, 0, 2.0f)),
            Map.entry("minecraft:iron_boots",      r(null, null, null, 0, 2.0f)),

            // Golden tools
            Map.entry("minecraft:golden_sword",   r(null, null, null, 0, 4.0f)),
            Map.entry("minecraft:golden_shovel",  r(null, null, null, 0, 2.6f)),
            Map.entry("minecraft:golden_pickaxe", r(null, null, null, 0, 8.0f)),
            Map.entry("minecraft:golden_axe",     r(null, null, null, 0, 8.0f)),
            Map.entry("minecraft:golden_hoe",     r(null, null, null, 0, 4.0f)),

            // Golden armor
            Map.entry("minecraft:golden_helmet",     r(null, null, null, 0, 4.0f)),
            Map.entry("minecraft:golden_chestplate", r(null, null, null, 0, 4.0f)),
            Map.entry("minecraft:golden_leggings",   r(null, null, null, 0, 4.0f)),
            Map.entry("minecraft:golden_boots",      r(null, null, null, 0, 4.0f)),

            // Diamond tools
            Map.entry("minecraft:diamond_sword",   r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:diamond_shovel",  r(null, null, null, 0, 0.30f)),
            Map.entry("minecraft:diamond_pickaxe", r(null, null, null, 0, 1.00f)),
            Map.entry("minecraft:diamond_axe",     r(null, null, null, 0, 1.00f)),
            Map.entry("minecraft:diamond_hoe",     r(null, null, null, 0, 0.50f)),

            // Diamond armor
            Map.entry("minecraft:diamond_helmet",     r(null, null, null, 0, 6.0f)),
            Map.entry("minecraft:diamond_chestplate", r(null, null, null, 0, 6.0f)),
            Map.entry("minecraft:diamond_leggings",   r(null, null, null, 0, 6.0f)),
            Map.entry("minecraft:diamond_boots",      r(null, null, null, 0, 6.0f)),

            // Netherite tools
            Map.entry("minecraft:netherite_sword",   r(null, null, null, 0, 0.60f)),
            Map.entry("minecraft:netherite_shovel",  r(null, null, null, 0, 0.40f)),
            Map.entry("minecraft:netherite_pickaxe", r(null, null, null, 0, 1.10f)),
            Map.entry("minecraft:netherite_axe",     r(null, null, null, 0, 1.10f)),
            Map.entry("minecraft:netherite_hoe",     r(null, null, null, 0, 0.75f)),

            // Netherite armor
            Map.entry("minecraft:netherite_helmet",     r(null, null, null, 0, 7.0f)),
            Map.entry("minecraft:netherite_chestplate", r(null, null, null, 0, 7.0f)),
            Map.entry("minecraft:netherite_leggings",   r(null, null, null, 0, 7.0f)),
            Map.entry("minecraft:netherite_boots",      r(null, null, null, 0, 7.0f)),

            // Leather armor
            Map.entry("minecraft:leather_helmet",     r(null, null, null, 0, 1.0f)),
            Map.entry("minecraft:leather_chestplate", r(null, null, null, 0, 1.0f)),
            Map.entry("minecraft:leather_leggings",   r(null, null, null, 0, 1.0f)),
            Map.entry("minecraft:leather_boots",      r(null, null, null, 0, 1.0f)),

            // String-based gear
            Map.entry("minecraft:fishing_rod",       r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:bow",               r(null, null, null, 0, 0.50f)),
            Map.entry("minecraft:carrot_on_a_stick", r(null, null, null, 0, 0.50f)),

            // String-based (with explicit materials/durability)
            Map.entry("minecraft:crossbow",              r("minecraft:string", 326, 3, 0, 0.50f)),
            Map.entry("minecraft:warped_fungus_on_a_stick", r("minecraft:string", 100, 3, 0, 0.50f)),

            // Other
            Map.entry("minecraft:elytra",  r("minecraft:phantom_membrane", 432, 8, 0, 3.0f)),
            Map.entry("minecraft:trident", r("minecraft:prismarine_crystals", 250, 16, 0, 3.0f)),
            Map.entry("minecraft:mace",    r("minecraft:breeze_rod", 250, 4, 0, 3.0f))
    );

    private static Repairable r(String mat, Integer maxDur, Integer minQty, int minLvl, float xpMul) {
        Repairable rr = new Repairable();
        rr.repairMaterial = mat;
        rr.maximumDurability = maxDur;
        rr.minimumQuantity = minQty;
        rr.minimumLevel = minLvl;
        rr.xpMultiplier = xpMul;
        return rr;
    }

    public RepairConfig() {
        super(SkillType.REPAIR);
        bossbarColor = "PURPLE";
    }
}
