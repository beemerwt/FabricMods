package com.github.beemerwt.mcrpg.config.ability;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.TreasureEntry;

import java.util.List;
import java.util.Map;

public class TreasureFindingConfig extends AbilityConfig {
    @JankComment("""
        Treasure drops that can be found while excavating.
        'amount' is how many of the item you get if it drops.
        'xp' is how much XP you get for finding the item.
        'dropChance' is the percentage chance (0.0 - 100.0) of the item dropping.
        'levelRequirement' is the Excavation level required before the item can drop.
        'dropsFrom' is a list of block IDs that this item can drop from.
        """)
    public Map<String, TreasureEntry> treasures = Map.ofEntries(
        Map.entry("minecraft:heart_of_the_sea", new TreasureEntry(1, 9999, 0.01, 900, List.of("minecraft:mud"))),
        Map.entry("minecraft:potato", new TreasureEntry(1, 50, 3.0, 100, List.of("minecraft:dirt", "minecraft:mud"))),
        Map.entry("minecraft:spyglass", new TreasureEntry(1, 500, 0.1, 70, List.of("minecraft:mud", "minecraft:dirt"))),
        Map.entry("minecraft:stick", new TreasureEntry(2, 50, 2.0, 10, List.of("minecraft:mud",
            "minecraft:muddy_mangrove_roots"))),
        Map.entry("minecraft:feather", new TreasureEntry(3, 100, 1.0, 50, List.of("minecraft:mud"))),
        Map.entry("minecraft:trident", new TreasureEntry(1, 100, 0.02, 400, List.of("minecraft:mud",
            "minecraft:clay", "minecraft:muddy_mangrove_roots"))),
        Map.entry("minecraft:cake", new TreasureEntry(1, 3000, 0.05, 750, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mycelium",
            "minecraft:soul_sand", "minecraft:soul_soil"))),
        Map.entry("minecraft:gunpowder", new TreasureEntry(1, 30, 10.0, 100, List.of("minecraft:gravel"))),
        Map.entry("minecraft:bone", new TreasureEntry(1, 30, 10.0, 200, List.of("minecraft:gravel", "minecraft:mud"))),
        Map.entry("minecraft:apple", new TreasureEntry(1, 100, 0.1, 250, List.of("minecraft:grass_block",
            "minecraft:mycelium"))),
        Map.entry("minecraft:slime_ball", new TreasureEntry(1, 100, 5.0, 150, List.of("minecraft:clay"))),
        Map.entry("minecraft:bucket", new TreasureEntry(1, 100, 0.1, 500, List.of("minecraft:clay"))),
        Map.entry("minecraft:netherrack", new TreasureEntry(1, 30, 0.5, 850, List.of("minecraft:gravel"))),
        Map.entry("minecraft:red_mushroom", new TreasureEntry(1, 80, 0.5, 500, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:mycelium",
            "minecraft:mud"))),
        Map.entry("minecraft:brown_mushroom", new TreasureEntry(1, 80, 0.5, 500, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:mycelium",
            "minecraft:mud"))),
        Map.entry("minecraft:egg", new TreasureEntry(1, 100, 1.0, 250, List.of("minecraft:grass_block"))),
        Map.entry("minecraft:soul_sand", new TreasureEntry(1, 80, 0.5, 650, List.of("minecraft:sand",
            "minecraft:red_sand"))),
        Map.entry("minecraft:clock", new TreasureEntry(1, 100, 0.1, 500, List.of("minecraft:clay"))),
        Map.entry("minecraft:cobweb", new TreasureEntry(1, 150, 5.0, 750, List.of("minecraft:clay"))),
        Map.entry("minecraft:string", new TreasureEntry(1, 200, 5.0, 250, List.of("minecraft:clay"))),
        Map.entry("minecraft:glowstone_dust", new TreasureEntry(1, 80, 5.0, 50, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:mycelium"))),
        Map.entry("minecraft:music_disc_13", new TreasureEntry(1, 3000, 0.05, 250, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mycelium",
            "minecraft:soul_sand", "minecraft:soul_soil"))),
        Map.entry("minecraft:music_disc_cat", new TreasureEntry(1, 3000, 0.05, 250, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mycelium",
            "minecraft:soul_sand", "minecraft:soul_soil"))),
        Map.entry("minecraft:diamond", new TreasureEntry(1, 1000, 0.13, 350, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mycelium",
            "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:mud"))),
        Map.entry("minecraft:cocoa_beans", new TreasureEntry(1, 100, 1.33, 350, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:mycelium",
            "minecraft:mud"))),
        Map.entry("minecraft:quartz", new TreasureEntry(1, 100, 0.5, 850, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:mycelium", "minecraft:soul_sand",
            "minecraft:soul_soil"))),
        Map.entry("minecraft:name_tag", new TreasureEntry(1, 3000, 0.05, 250, List.of("minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:grass_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mycelium",
            "minecraft:soul_sand", "minecraft:soul_soil")))
    );
}
