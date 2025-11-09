package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.ability.LeafBlower;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.ability.TreeFeller;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.item.ItemStack;

public class Woodcutting extends GatheringSkill<WoodcuttingConfig> {
    public Woodcutting() {
        super(SkillType.WOODCUTTING);
        this.addAbility(new LeafBlower(() -> config.leafBlower));
        this.addAbility(new DoubleDrops<>(this.config, () -> this.config.doubleDrops, this::isValidTool));
        this.addAbility(new TreeFeller(() -> this.config.treeFeller));
    }

    @Override
    protected boolean isValidTool(ItemStack stack) { return ItemClassifier.isAxe(stack.getItem()); }
}
