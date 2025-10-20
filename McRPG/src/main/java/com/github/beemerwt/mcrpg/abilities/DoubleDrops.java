package com.github.beemerwt.mcrpg.abilities;


import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class DoubleDrops {

    /**
     * Spawns a second copy of all drops at the given position if the proc chance succeeds.
     * @param doubleDrops Config for this ability.
     * @param skillLevel Player's skill level.
     * @param world World returned from break event.
     * @param pos Position of the block broken.
     * @param block The block that was broken.
     * @param drops The original drops from the block break event.
     * @return True if the ability proc'd and drops were spawned, false otherwise.
     */
    public static boolean processTrigger(DoubleDropsConfig doubleDrops, int skillLevel,
                                  ServerWorld world, BlockPos pos, Block block, List<ItemStack> drops)
    {
        double chance = Leveling.getScaledPercentage(doubleDrops.baseChance, doubleDrops.maxChance, skillLevel);
        if (Math.random() > chance) return false;

        McRPG.getLogger().debug("Double drop proc on block {}", block.getName());

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (var s : drops) {
            // Spawn through ServerWorld so Telekinesis mixin sees it and captures.
            var entity = new ItemEntity(world, cx, cy, cz, s.copy());
            world.spawnEntity(entity);
        }

        return true;
    }
}
