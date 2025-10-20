package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.IHasBlocks;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.config.ability.LeafBlowerConfig;
import com.github.beemerwt.mcrpg.config.ability.TreeFellerConfig;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;
import java.util.Optional;

@JanksonObject
public class WoodcuttingConfig extends SkillConfig implements IHasBlocks {

    @JankKey("Double Drops")
    public DoubleDropsConfig doubleDrops = new DoubleDropsConfig();

    @JankKey("Tree Feller")
    public TreeFellerConfig treeFeller = new TreeFellerConfig();

    @JankKey("Leaf Blower")
    public LeafBlowerConfig leafBlower = new LeafBlowerConfig();

    @JankComment("Block XP values")
    private Map<String, Integer> blocks = Map.ofEntries(
        Map.entry("minecraft:crimson_stem", 35),
        Map.entry("minecraft:warped_stem", 35),
        Map.entry("minecraft:oak_log", 70),
        Map.entry("minecraft:cherry_log", 105),
        Map.entry("minecraft:spruce_log", 80),
        Map.entry("minecraft:birch_log", 90),
        Map.entry("minecraft:jungle_log", 100),
        Map.entry("minecraft:acacia_log", 90),
        Map.entry("minecraft:dark_oak_log", 90),
        Map.entry("minecraft:pale_oak_log", 130),
        Map.entry("minecraft:mangrove_log", 95),
        Map.entry("minecraft:mangrove_roots", 10),
        Map.entry("minecraft:muddy_mangrove_roots", 10),
        Map.entry("minecraft:red_mushroom_block", 70),
        Map.entry("minecraft:brown_mushroom_block", 70),
        Map.entry("minecraft:mushroom_stem", 80)
    );

    public WoodcuttingConfig() {
        super(SkillType.WOODCUTTING);
        bossbarColor = "GREEN";
    }

    @Override
    public boolean hasAbility(ActiveAbilityType activeAbilityType) {
        return activeAbilityType == ActiveAbilityType.TREE_FELLER;
    }

    @Override
    public Optional<AbilityConfig> getAbilityConfig(ActiveAbilityType activeAbilityType) {
        return switch (activeAbilityType) {
            case TREE_FELLER -> Optional.of(treeFeller);
            default -> Optional.empty();
        };
    }

    @Override
    public Map<String, Integer> getBlocks() {
        return blocks;
    }
}
