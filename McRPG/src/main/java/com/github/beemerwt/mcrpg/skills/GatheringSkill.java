package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.config.IBlacklist;
import com.github.beemerwt.mcrpg.config.IBlockConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.service.XpModifier;
import com.github.beemerwt.mcrpg.util.EventBus;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public abstract class GatheringSkill<C extends SkillConfig & IBlockConfig> extends Skill<C> {
    public GatheringSkill(SkillType skillType) {
        super(skillType);
        EventBus.subscribe(BreakBlockEvent.class, this::onBlockBreak);
    }

    protected abstract boolean isValidTool(ItemStack stack);

    /**
     * Determines if XP can be awarded for this block break
     *
     * @param ctx Context of the block break
     * @return True if XP can be awarded, false otherwise
     */
    boolean canAwardXp(BreakBlockEvent ctx) {
        return !PlacedBlockTracker.get(ctx.world()).isMarked(ctx.pos());
    }

    protected void onBlockBreak(BreakBlockEvent ctx) {
        if (!canAwardXp(ctx)) return;

        if (config instanceof IBlacklist ibl) {
            var blockId = Registries.BLOCK.getId(ctx.block()).toString();
            if (ibl.isBlacklisted(blockId)) return;
        }

        long baseXp = Leveling.resolveBlockXp(config.getBlocks(), ctx.block());
        if (baseXp <= 0) return;

        XpModifier.addFlat(ctx.player(), skillType, baseXp * config.xpModifier);
    }
}
