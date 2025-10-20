package com.github.beemerwt.mcrpg.config.skills;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankKey;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.IHasBlocks;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.ability.*;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.SkillType;

import java.util.Map;
import java.util.Optional;

@JanksonObject
public class HerbalismConfig extends SkillConfig implements IHasBlocks {

    @JankKey("Green Thumb")
    public GreenThumbConfig greenThumb = new GreenThumbConfig();

    @JankKey("Double Drops")
    public DoubleDropsConfig doubleDrops = new DoubleDropsConfig();

    @JankKey("Green Terra")
    public GreenTerraConfig greenTerra = new GreenTerraConfig();

    // Not implemented yet
    // @JankKey("Hylian Luck")
    // public HylianLuckConfig hylianLuck = new HylianLuckConfig();

    @JankKey("Shroom Thumb")
    public ShroomThumbConfig shroomThumb = new ShroomThumbConfig();

    @JankComment("Crop XP values")
    public Map<String, Integer> blocks = Map.ofEntries(
            Map.entry("minecraft:bush", 11),
            Map.entry("minecraft:cactus_flower", 60),
            Map.entry("minecraft:firefly_bush", 15),
            Map.entry("minecraft:leaf_litter", 2),
            Map.entry("minecraft:short_dry_grass", 6),
            Map.entry("minecraft:tall_dry_grass", 12),
            Map.entry("minecraft:wildflowers", 15),
            Map.entry("minecraft:eyeblossom", 66),
            Map.entry("minecraft:open_eyeblossom", 66),
            Map.entry("minecraft:closed_eyeblossom", 66),
            Map.entry("minecraft:pitcher_plant", 160),
            Map.entry("minecraft:pink_petals", 10),
            Map.entry("minecraft:small_dripleaf", 140),
            Map.entry("minecraft:big_dripleaf", 140),
            Map.entry("minecraft:cave_vines", 90),
            Map.entry("minecraft:cave_vines_plant", 90),
            Map.entry("minecraft:pale_hanging_moss", 150),
            Map.entry("minecraft:pale_moss_block", 10),
            Map.entry("minecraft:pale_moss_carpet", 10),
            Map.entry("minecraft:glow_lichen", 5),
            Map.entry("minecraft:moss_block", 150),
            Map.entry("minecraft:crimson_roots", 6),
            Map.entry("minecraft:warped_roots", 6),
            Map.entry("minecraft:nether_wart_block", 3),
            Map.entry("minecraft:warped_wart_block", 3),
            Map.entry("minecraft:nether_sprouts", 10),
            Map.entry("minecraft:crimson_fungus", 50),
            Map.entry("minecraft:warped_fungus", 50),
            Map.entry("minecraft:shroomlight", 250),
            Map.entry("minecraft:bee_nest", 200),
            Map.entry("minecraft:sweet_berry_bush", 50),
            Map.entry("minecraft:seagrass", 10),
            Map.entry("minecraft:tall_seagrass", 10),
            Map.entry("minecraft:kelp", 3),
            Map.entry("minecraft:kelp_plant", 3),
            Map.entry("minecraft:tube_coral", 80),
            Map.entry("minecraft:brain_coral", 90),
            Map.entry("minecraft:bubble_coral", 75),
            Map.entry("minecraft:fire_coral", 120),
            Map.entry("minecraft:horn_coral", 175),
            Map.entry("minecraft:tube_coral_fan", 80),
            Map.entry("minecraft:brain_coral_fan", 90),
            Map.entry("minecraft:bubble_coral_fan", 75),
            Map.entry("minecraft:fire_coral_fan", 120),
            Map.entry("minecraft:horn_coral_fan", 175),
            Map.entry("minecraft:tube_coral_wall_fan", 80),
            Map.entry("minecraft:brain_coral_wall_fan", 90),
            Map.entry("minecraft:bubble_coral_wall_fan", 75),
            Map.entry("minecraft:fire_coral_wall_fan", 120),
            Map.entry("minecraft:horn_coral_wall_fan", 175),
            Map.entry("minecraft:dead_tube_coral", 10),
            Map.entry("minecraft:dead_brain_coral", 10),
            Map.entry("minecraft:dead_bubble_coral", 10),
            Map.entry("minecraft:dead_fire_coral", 10),
            Map.entry("minecraft:dead_horn_coral", 10),
            Map.entry("minecraft:dead_tube_coral_fan", 10),
            Map.entry("minecraft:dead_brain_coral_fan", 10),
            Map.entry("minecraft:dead_bubble_coral_fan", 10),
            Map.entry("minecraft:dead_fire_coral_fan", 10),
            Map.entry("minecraft:dead_horn_coral_fan", 10),
            Map.entry("minecraft:dead_tube_coral_wall_fan", 10),
            Map.entry("minecraft:dead_brain_coral_wall_fan", 10),
            Map.entry("minecraft:dead_bubble_coral_wall_fan", 10),
            Map.entry("minecraft:dead_fire_coral_wall_fan", 10),
            Map.entry("minecraft:dead_horn_coral_wall_fan", 10),
            Map.entry("minecraft:allium", 300),
            Map.entry("minecraft:azure_bluet", 150),
            Map.entry("minecraft:blue_orchid", 150),
            Map.entry("minecraft:brown_mushroom", 150),
            Map.entry("minecraft:cactus", 30),
            Map.entry("minecraft:chorus_flower", 25),
            Map.entry("minecraft:chorus_plant", 1),
            Map.entry("minecraft:carrots", 50),
            Map.entry("minecraft:cocoa", 30),
            Map.entry("minecraft:potatoes", 50),
            Map.entry("minecraft:wheat", 50),
            Map.entry("minecraft:beetroot", 50),
            Map.entry("minecraft:beetroots", 50),
            Map.entry("minecraft:nether_wart", 50),
            Map.entry("minecraft:dead_bush", 30),
            Map.entry("minecraft:lilac", 50),
            Map.entry("minecraft:melon", 20),
            Map.entry("minecraft:orange_tulip", 150),
            Map.entry("minecraft:oxeye_daisy", 150),
            Map.entry("minecraft:peony", 50),
            Map.entry("minecraft:pink_tulip", 150),
            Map.entry("minecraft:poppy", 100),
            Map.entry("minecraft:pumpkin", 20),
            Map.entry("minecraft:red_mushroom", 150),
            Map.entry("minecraft:red_tulip", 150),
            Map.entry("minecraft:rose_bush", 50),
            Map.entry("minecraft:fern", 10),
            Map.entry("minecraft:grass", 10),
            Map.entry("minecraft:short_grass", 10),
            Map.entry("minecraft:sugar_cane", 30),
            Map.entry("minecraft:sunflower", 50),
            Map.entry("minecraft:tall_grass", 50),
            Map.entry("minecraft:large_fern", 50),
            Map.entry("minecraft:vine", 10),
            Map.entry("minecraft:weeping_vines_plant", 10),
            Map.entry("minecraft:twisting_vines_plant", 10),
            Map.entry("minecraft:lily_pad", 100),
            Map.entry("minecraft:white_tulip", 150),
            Map.entry("minecraft:dandelion", 100),
            Map.entry("minecraft:bamboo", 10),
            Map.entry("minecraft:cornflower", 150),
            Map.entry("minecraft:lily_of_the_valley", 150),
            Map.entry("minecraft:wither_rose", 500),
            Map.entry("minecraft:torchflower", 90)
    );

    @JankComment("Crops that can trigger double drops")
    public Map<String, Boolean> doubleDropCrops = Map.ofEntries(
            Map.entry("minecraft:bush", true),
            Map.entry("minecraft:cactus_flower", true),
            Map.entry("minecraft:firefly_bush", true),
            Map.entry("minecraft:leaf_litter", true),
            Map.entry("minecraft:short_dry_grass", true),
            Map.entry("minecraft:tall_dry_grass", true),
            Map.entry("minecraft:wildflowers", true),
            Map.entry("minecraft:eyeblossom", true),
            Map.entry("minecraft:open_eyeblossom", true),
            Map.entry("minecraft:closed_eyeblossom", true),
            Map.entry("minecraft:pitcher_plant", true),
            Map.entry("minecraft:torchflower", true),
            Map.entry("minecraft:pink_petals", true),
            Map.entry("minecraft:glow_berries", true),
            Map.entry("minecraft:cave_vines", true),
            Map.entry("minecraft:cave_vines_plant", true),
            Map.entry("minecraft:moss_block", true),
            Map.entry("minecraft:sweet_berry_bush", true),
            Map.entry("minecraft:weeping_vines", true),
            Map.entry("minecraft:twisting_vines", true),
            Map.entry("minecraft:shroomlight", true),
            Map.entry("minecraft:crimson_stem", true),
            Map.entry("minecraft:warped_stem", true),
            Map.entry("minecraft:crimson_roots", true),
            Map.entry("minecraft:warped_roots", true),
            Map.entry("minecraft:nether_wart_block", true),
            Map.entry("minecraft:warped_wart_block", true),
            Map.entry("minecraft:bamboo_sapling", true),
            Map.entry("minecraft:crimson_fungus", true),
            Map.entry("minecraft:warped_fungus", true),
            Map.entry("minecraft:chorus_fruit", true),
            Map.entry("minecraft:chorus_plant", true),
            Map.entry("minecraft:beetroots", true),
            Map.entry("minecraft:beetroot", true),
            Map.entry("minecraft:brown_mushroom", true),
            Map.entry("minecraft:cactus", true),
            Map.entry("minecraft:carrots", true),
            Map.entry("minecraft:carrot", true),
            Map.entry("minecraft:cocoa", true),
            Map.entry("minecraft:cocoa_beans", true),
            Map.entry("minecraft:wheat", true),
            Map.entry("minecraft:melon", true),
            Map.entry("minecraft:melon_slice", true),
            Map.entry("minecraft:nether_wart", true),
            Map.entry("minecraft:potatoes", true),
            Map.entry("minecraft:potato", true),
            Map.entry("minecraft:pumpkin", true),
            Map.entry("minecraft:red_mushroom", true),
            Map.entry("minecraft:sugar_cane", true),
            Map.entry("minecraft:vine", true),
            Map.entry("minecraft:lily_pad", true),
            Map.entry("minecraft:red_tulip", true),
            Map.entry("minecraft:white_tulip", true),
            Map.entry("minecraft:pink_tulip", true),
            Map.entry("minecraft:orange_tulip", true),
            Map.entry("minecraft:dandelion", true),
            Map.entry("minecraft:poppy", true),
            Map.entry("minecraft:blue_orchid", true),
            Map.entry("minecraft:allium", true),
            Map.entry("minecraft:azure_bluet", true),
            Map.entry("minecraft:oxeye_daisy", true),
            Map.entry("minecraft:sunflower", true),
            Map.entry("minecraft:lilac", true),
            Map.entry("minecraft:rose_bush", true),
            Map.entry("minecraft:peony", true),
            Map.entry("minecraft:lily_of_the_valley", true)
    );

    public HerbalismConfig() {
        super(SkillType.HERBALISM);
        bossbarColor = "GREEN";
    }

    @Override
    public Map<String, Integer> getBlocks() {
        return blocks;
    }

    @Override
    public boolean hasAbility(ActiveAbilityType activeAbilityType) {
        return activeAbilityType == ActiveAbilityType.GREEN_TERRA;
    }

    @Override
    public Optional<AbilityConfig> getAbilityConfig(ActiveAbilityType activeAbilityType) {
        return switch (activeAbilityType) {
            case GREEN_TERRA -> Optional.of(greenTerra);
            default -> Optional.empty();
        };
    }
}
