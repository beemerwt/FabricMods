package com.github.beemerwt.mcrpg.ability;


import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.*;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.service.XpModifier;
import com.github.beemerwt.mcrpg.util.EventBus;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DoubleDrops<C extends SkillConfig & IBlockConfig> extends PassiveAbility<C, DoubleDropsConfig> {
    Predicate<ItemStack> toolCheck;

    public DoubleDrops(C parentConfig, Supplier<DoubleDropsConfig> provider, Predicate<ItemStack> toolCheck) {
        super(parentConfig.skillType, provider);
        this.toolCheck = toolCheck;
        EventBus.subscribe(BreakBlockEvent.class, this::onBlockBreak);
    }

    private void onBlockBreak(BreakBlockEvent e) {
        if (!config.enabled) return;
        if (!toolCheck.test(e.tool())) return;

        var block = e.block();

        var key = Registries.BLOCK.getId(block).toString();
        if (isBlacklisted(key)) return;

        var blocks = skillConfig.getBlocks();
        var xp = Leveling.resolveBlockXp(blocks, block);
        if (xp <= 0 && !isWhitelisted(key)) return;

        int level = Leveling.getLevel(e.player(), skillType);

        double chance = Leveling.getScaledPercentage(config.baseChance, config.maxChance, level);
        double roll = Math.random();
        if (roll > chance) return;

        McRPG.getLogger().debug("Double drop proc on block {}", block.getName());

        BlockPos pos = e.pos();
        ServerWorld world = e.world();
        List<ItemStack> drops = Block.getDroppedStacks(e.state(), world, pos, e.be(), e.player(), e.tool());

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (var s : drops) {
            // Spawn through ServerWorld so Telekinesis mixin sees it and captures.
            var entity = new ItemEntity(world, cx, cy, cz, s.copy());
            world.spawnEntity(entity);
        }

        XpModifier.addFlat(e.player(), skillType, xp * skillConfig.xpModifier);
    }

    private boolean isWhitelisted(String blockId) {
        if (this.skillConfig instanceof IWhitelist iwl)
            return iwl.getWhitelist().contains(blockId);

        return false;
    }

    private boolean isBlacklisted(String blockId) {
        if (this.skillConfig instanceof IBlacklist ibl)
            return ibl.getBlacklist().contains(blockId);

        return false;
    }

    @Override public String id() { return "double_drops"; }
}
