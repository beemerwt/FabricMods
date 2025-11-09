package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.ability.GigaDrillBreaker;
import com.github.beemerwt.mcrpg.ability.TreasureFinding;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.item.ItemStack;

public class Excavation extends GatheringSkill<ExcavationConfig> {

    public Excavation() {
        super(SkillType.EXCAVATION);
        this.addAbility(new TreasureFinding(() -> this.config.treasureFinding));
        this.addAbility(new GigaDrillBreaker(() -> this.config.gigaDrillBreaker));
    }

    @Override
    protected boolean isValidTool(ItemStack stack) {
        return ItemClassifier.isShovel(stack.getItem());
    }
}
