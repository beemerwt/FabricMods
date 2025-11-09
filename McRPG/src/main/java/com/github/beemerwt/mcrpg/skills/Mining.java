package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.ability.SuperBreaker;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.item.ItemStack;

public class Mining extends GatheringSkill<MiningConfig> {
    public Mining() {
        super(SkillType.MINING);
        this.addAbility(new DoubleDrops<>(this.config, () -> this.config.doubleDrops, this::isValidTool));
        this.addAbility(new SuperBreaker(() -> this.config.superBreaker));
    }

    @Override
    protected boolean isValidTool(ItemStack stack) {
        return ItemClassifier.isPickaxe(stack.getItem());
    }
}
