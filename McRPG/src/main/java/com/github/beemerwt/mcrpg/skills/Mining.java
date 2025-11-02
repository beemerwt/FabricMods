package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.events.PlayerEvents;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.abilities.DoubleDrops;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import java.util.List;

public class Mining {
    private Mining() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(Mining::onBlockMined);
    }

    public static void onBlockMined(World world,
                                    PlayerEntity p,
                                    BlockPos pos,
                                    BlockState state,
                                    @Nullable BlockEntity entity)
    {
        if (!(world instanceof ServerWorld sw)) return;
        if (!(p instanceof ServerPlayerEntity player)) return;
        if (PlacedBlockTracker.get(sw).isMarked(pos)) return; // Ignore player-placed blocks

        MiningConfig cfg = ConfigManager.getSkillConfig(SkillType.MINING);
        var blocks = cfg.getBlocks();
        var block = state.getBlock();

        var blockKey = Registries.BLOCK.getId(block).toString();
        var isWhitelisted = cfg.whitelist.contains(blockKey);

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0 && !isWhitelisted) return; // Allow whitelisted blocks with 0 XP

        ItemStack tool = player.getMainHandStack();
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, entity, player, tool);

        int level = Leveling.getLevel(player, SkillType.MINING);

        // Only trigger skills if the player is using a pickaxe
        if (ItemClassifier.isPickaxe(tool.getItem()))
            // TODO: Implement whitelist/blacklist for blocks that can trigger double drops
            if (DoubleDrops.processTrigger(cfg.doubleDrops, cfg.blacklist, level,
                    player.getEntityWorld(), pos, block, drops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Mining XP awarded to {} for block broken {}",
                awarded, player.getStringifiedName(), block.getName());
        Leveling.addXp(player, SkillType.MINING, awarded);
    }
}
