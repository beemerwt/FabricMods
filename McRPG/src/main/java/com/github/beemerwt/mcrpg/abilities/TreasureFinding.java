package com.github.beemerwt.mcrpg.abilities;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureFinding {

    /**
     * Optimized treasure finding trigger processor.
     * @param treasures Map of treasure id to treasure entry from config.
     * @param skillLevel Player's skill level.
     * @param world World returned from break event.
     * @param pos Position of the block broken.
     * @param block The block that was broken.
     * @return XP awarded from treasure, or 0 if none.
     */
    public static long processTrigger(Map<String, ExcavationConfig.TreasureEntry> treasures,
                                      int skillLevel,
                                      ServerWorld world,
                                      BlockPos pos,
                                      Block block) {
        if (treasures == null || treasures.isEmpty()) return 0L;

        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Fast uniform reservoir among certainties (p >= 1).
        Map.Entry<String, ExcavationConfig.TreasureEntry> certPick = null;
        int certCount = 0;

        // Weighted reservoir among non-certainties using weights lambda_i = -log1p(-p).
        Map.Entry<String, ExcavationConfig.TreasureEntry> weightedPick = null;
        double sumLambda = 0.0;

        final String blockKey = Registries.BLOCK.getId(block).toString();

        for (Map.Entry<String, ExcavationConfig.TreasureEntry> me : treasures.entrySet()) {
            final ExcavationConfig.TreasureEntry e = me.getValue();

            // Level gate
            if (skillLevel < e.levelRequirement) continue;

            // Block source gate
            if (e.dropsFrom != null && !e.dropsFrom.contains(blockKey)) continue;

            // Probability (hot path): clamp once, skip work for <=0.
            double p = getChance(e);
            if (p <= 0.0) continue;
            if (p >= 1.0) {
                // Reservoir sample uniformly among certainties.
                // Equivalent to: pick current with probability 1/(certCount+1).
                certCount++;
                if (rng.nextInt(certCount) == 0) {
                    certPick = me;
                }
                // When there is any certainty, the outcome is definitely a win among certs.
                // Keep scanning in case there are more certs (to maintain uniformity), but
                // skip non-cert weighting work to keep time linear with minimal math calls.
                continue;
            }

            // Non-certainty: lambda_i = -log1p(-p)
            final double li = -Math.log1p(-p);
            if (li <= 0.0) continue; // extremely tiny or numerical underflow

            // Weighted reservoir sampling for picking index ~ lambda_i / sum(lambda)
            final double newSum = sumLambda + li;
            // Select current with probability li / newSum
            if (rng.nextDouble() < (li / newSum))
                weightedPick = me;
            sumLambda = newSum;
        }

        // If any certainty existed, select from cert reservoir (uniform over all p>=1).
        if (certCount > 0) {
            spawnItem(world, pos, certPick.getKey(), certPick.getValue().amount);
            return certPick.getValue().xp;
        }

        // No certainties. Handle "no-win" with probability exp(-S).
        if (sumLambda <= 0.0) return 0L; // all p==0 or filtered out

        if (rng.nextDouble() < Math.exp(-sumLambda)) return 0L; // no winner

        // Conditional on winning, weightedPick has distribution ~ lambda_i / sumLambda.
        // By construction, weightedPick is non-null if sumLambda > 0.
        if (weightedPick != null) {
            spawnItem(world, pos, weightedPick.getKey(), weightedPick.getValue().amount);
            return weightedPick.getValue().xp;
        }

        return 0L;
    }

    private static double getChance(ExcavationConfig.TreasureEntry e) {
        double p = e.dropChance * 0.01;
        if (p <= 0.0) return 0.0;
        return Math.min(p, 1.0);
    }

    private static void spawnItem(World world, BlockPos pos, String itemId, int amount) {
        var item = Registries.ITEM.get(Identifier.tryParse(itemId));
        if (item == Items.AIR) return; // invalid item

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        var stack = new ItemStack(item, amount);
        var entity = new ItemEntity(world, cx, cy, cz, stack);
        world.spawnEntity(entity);
    }
}
