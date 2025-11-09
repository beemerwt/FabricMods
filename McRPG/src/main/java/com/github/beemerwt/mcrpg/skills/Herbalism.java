package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.ability.GreenThumb;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.ability.DoubleDrops;
import com.github.beemerwt.mcrpg.ability.GreenTerra;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;

public class Herbalism extends GatheringSkill<HerbalismConfig> {

    // TODO: DoubleDrops only apply for the certain crops listed in the config

    public Herbalism() {
        super(SkillType.HERBALISM);
        this.addAbility(new DoubleDrops<>(config, () -> config.doubleDrops, this::isValidTool));
        this.addAbility(new GreenTerra(() -> config.greenTerra));
        this.addAbility(new GreenThumb(() -> config.greenThumb));
    }

    @Override
    boolean canAwardXp(BreakBlockEvent ctx) {
        return Growth.shouldAwardXpOnBreak(ctx.world(), ctx.pos(), ctx.state());
    }

    @Override
    protected boolean isValidTool(ItemStack stack) {
        return ItemClassifier.isHoe(stack.getItem());
    }
}
