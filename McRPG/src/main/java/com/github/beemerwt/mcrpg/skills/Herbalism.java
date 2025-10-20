package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.CropBlockEvents;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.abilities.DoubleDrops;
import com.github.beemerwt.mcrpg.abilities.GreenTerra;
import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.proxies.CropBlockProxy;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.joml.Math;

import java.util.List;

public class Herbalism {

    public static void register() {
        CropBlockEvents.RANDOM_TICK.register(Herbalism::onRandomTick);
    }

    private static void onRandomTick(ServerWorld world, CropBlockProxy proxy, Random random) {
        float m = CropMarkers.get(world).getMultiplier(proxy.pos());
        if (m <= 1.0f) return;

        int extra = (int) Math.floor(m - 1.0f);
        float frac = (m - 1.0f) - extra;
        for (int i = 0; i < extra; i++)
            proxy.randomTick(world, random);

        if (random.nextFloat() < frac)
            proxy.randomTick(world, random);
    }

    public static void onCropBroken(ServerPlayerEntity player,
                                    ServerWorld world,
                                    BlockPos pos,
                                    BlockState state,
                                    List<ItemStack> drops)
    {
        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);
        var blocks = cfg.getBlocks();
        var block = state.getBlock();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        if (!Growth.isMature(state)) return; // Only award XP for fully grown crops

        // GREEN TERRA: queue a replant candidate while active
        GreenTerra.considerReplant(player, world, pos, state);

        int level = Leveling.getLevel(player, SkillType.HERBALISM);
        var id = Registries.BLOCK.getId(block);

        // Only trigger skills if the player is using a hoe and the crop supports double drops
        if (cfg.doubleDropCrops.get(id.toString()) != null)
            if (DoubleDrops.processTrigger(cfg.doubleDrops, level, player.getEntityWorld(), pos, block, drops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = org.joml.Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Herbalism XP awarded to {} for crop broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.HERBALISM, awarded);
    }
}
