package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.abilities.DoubleDrops;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.List;

public class Mining {
    private Mining() {}

    /**
     * Should be called when a player mines a block to handle Mining skill XP and abilities.
     * @param player The player who mined the block.
     * @param world The world the block was mined in.
     * @param pos The position of the block that was mined.
     * @param state The block state of the block that was mined.
     * @param drops The list of item drops from the block.
     */
    public static void onBlockMined(ServerPlayerEntity player,
                                    ServerWorld world,
                                    BlockPos pos,
                                    BlockState state,
                                    List<ItemStack> drops)
    {
        MiningConfig cfg = ConfigManager.getSkillConfig(SkillType.MINING);
        var blocks = cfg.getBlocks();
        var block = state.getBlock();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        int level = Leveling.getLevel(player, SkillType.MINING);

        // Only trigger skills if the player is using a pickaxe
        var tool = player.getMainHandStack().getItem();
        if (ItemClassifier.isPickaxe(tool) && BlockClassifier.isOre(block))
            // TODO: Implement whitelist/blacklist for blocks that can trigger double drops
            if (DoubleDrops.processTrigger(cfg.doubleDrops, level, player.getEntityWorld(), pos, block, drops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Mining XP awarded to {} for block broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.MINING, awarded);
    }
}
