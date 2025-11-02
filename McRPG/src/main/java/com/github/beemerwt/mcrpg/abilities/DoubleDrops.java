package com.github.beemerwt.mcrpg.abilities;


import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.DoubleDropsConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class DoubleDrops {
    /**
     * Spawns a second copy of all drops at the given position if the proc chance succeeds.
     * @param doubleDrops Config for this ability.
     * @param blacklist A list of explicitly disallowed block IDs.
     * @param skillLevel Player's skill level.
     * @param world World returned from break event.
     * @param pos Position of the block broken.
     * @param block The block that was broken.
     * @param drops The original drops from the block break event.
     * @return True if the ability proc'd and drops were spawned, false otherwise.
     */
    public static boolean processTrigger(
            DoubleDropsConfig doubleDrops, List<String> blacklist, int skillLevel,
            ServerWorld world, BlockPos pos, Block block, List<ItemStack> drops
    ) {
        var key = Registries.BLOCK.getId(block).toString();
        if (blacklist.contains(key)) return false;

        double chance = Leveling.getScaledPercentage(doubleDrops.baseChance, doubleDrops.maxChance, skillLevel);
        double roll = Math.random();
        if (roll > chance) return false;

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

    /**
     * Convenience overload of {@link #processTrigger(DoubleDropsConfig, List, int, ServerWorld, BlockPos, Block, List)}
     * that assumes no blocks are blacklisted.
     * <p>
     * This behaves identically to the full overload, but internally passes an empty blacklist.
     *
     * @param doubleDrops Config for this ability.
     * @param skillLevel Player's skill level.
     * @param world World where the block was broken.
     * @param pos Position of the block broken.
     * @param block The block that was broken.
     * @param drops The original drops from the block break event.
     * @return {@code true} if the ability proc'd and drops were spawned; {@code false} otherwise.
     * @see #processTrigger(DoubleDropsConfig, List, int, ServerWorld, BlockPos, Block, List)
     */
    public static boolean processTrigger(
            DoubleDropsConfig doubleDrops, int skillLevel,
            ServerWorld world, BlockPos pos, Block block, List<ItemStack> drops
    ) { return processTrigger(doubleDrops, List.of(), skillLevel, world, pos, block, drops); }
}
