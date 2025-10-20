package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.abilities.TreasureFinding;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.List;

public class Excavation {

    public static void onBlockDug(ServerPlayerEntity player,
                                  ServerWorld world,
                                  BlockPos pos,
                                  BlockState state,
                                  List<ItemStack> drops)
    {
        ExcavationConfig cfg = ConfigManager.getSkillConfig(SkillType.EXCAVATION);
        var blocks = cfg.getBlocks();
        var block = state.getBlock();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        int level = Leveling.getLevel(player, SkillType.EXCAVATION);
        var id = Registries.BLOCK.getId(block).toString();

        // Only trigger skills if the player is using a pickaxe
        var tool = player.getMainHandStack().getItem();
        if (ItemClassifier.isShovel(tool)) {
            long extraXp = TreasureFinding.processTrigger(cfg.treasures, level, player.getEntityWorld(), pos, block);
            blockXp += extraXp;
        }

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = Math.max(0L, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Excavation XP awarded to {} for block broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.EXCAVATION, awarded);
    }
}
