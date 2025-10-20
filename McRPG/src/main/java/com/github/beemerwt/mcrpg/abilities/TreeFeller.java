package com.github.beemerwt.mcrpg.abilities;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;

/**
 * McRPG Tree Feller — adaptation of mcMMO's original Tree Feller search.
 *
 * Algorithm:
 *  - If the block above the current center is a log: search a flat "cylinder" neighborhood
 *    (a disk of radius ~2 in X/Z) at the same Y.
 *  - Otherwise (branch/top): search the same cylinder at Y-1..Y..Y+1
 *    and also check the block directly below.
 *  - For any log found, add it to the removal set and schedule it as a new center to recurse.
 *  - For tree non-wood parts (leaves, roots, wart blocks, etc.), add to removal but don't recurse.
 *
 * Safety:
 *  - Hard threshold on total blocks to avoid nuking forests.
 *  - Optional "ineligible" predicate so you can skip player-placed logs if you track them.
 */
public final class TreeFeller {
    private TreeFeller() {}

    private static final int THRESHOLD = 1024;
    private static final boolean INCLUDE_NON_WOOD_PARTS = true;

    /**
     * Collects the tree starting at 'start' and processes each block:
     * - precomputes drops for Double Drops
     * - breaks the block
     * - damages the axe on logs
     * - awards per-block XP (doubled if Double Drops procs)
     * Returns the total (pre-modifier) XP earned.
     */
    public static long fellAndProcess(ServerPlayerEntity player, BlockPos start,
                                      int skillLevel, WoodcuttingConfig cfg) {
        var world = player.getEntityWorld();
        var treeBlocks = collect(world, start);
        if (treeBlocks.isEmpty()) return 0L;

        long totalXp = 0L;
        var ordered = treeBlocks.stream()
                .sorted(Comparator.comparingInt(Vec3i::getY))
                .toList();

        var tool = player.getMainHandStack();

        for (var pos : ordered) {
            var state = world.getBlockState(pos);
            var block = state.getBlock();

            // Resolve XP for THIS block (skip if non-xp)
            long xp = Leveling.resolveBlockXp(cfg.getBlocks(), block);
            if (xp <= 0) continue;

            // Build predicted drops BEFORE breaking so we can duplicate exactly
            var predictedDrops = predictedDropsFor(state, world, pos, player, tool);

            // Break (this path won't fire Fabric AFTER; that's fine for Telekinesis since we spawn dupes)
            if (!world.breakBlock(pos, true, player)) {
                McRPG.getLogger().warning("Tree Feller failed to break {} at {}",
                        Registries.BLOCK.getId(block), pos);
                continue;
            }

            // Axe durability on logs only; do not cancel the run mid-tree (let natural break happen)
            if (BlockClassifier.isLog(block) && tool.isDamageable())
                tool.damage(1, player, EquipmentSlot.MAINHAND);

            // Double Drops per block (spawns extra copies the Telekinesis mixin can capture)
            if (DoubleDrops.processTrigger(cfg.doubleDrops, skillLevel, world, pos, block, predictedDrops))
                xp *= 2;

            totalXp += Math.max(0, xp);
        }

        McRPG.getLogger().debug("Tree Feller felled {} blocks for {}",
                ordered.size(), player.getName().getString());
        return totalXp;
    }

    // ---- drops helper (predict identical to the real drop) ----

    private static List<ItemStack> predictedDropsFor(BlockState state, ServerWorld world,
                                                     BlockPos pos, ServerPlayerEntity player,
                                                     ItemStack tool) {
        try {
            // Newer Yarn: LootContextParameterSet.Builder for BLOCK
            var be = world.getBlockEntity(pos);
            return Block.getDroppedStacks(state, world, pos, be, player, tool);
        } catch (Throwable t) {
            // If mappings differ on your version, fall back to empty → no duplicate spawn.
            McRPG.getLogger().debug("Tree Feller: predictedDropsFor failed for {} at {}: {}",
                    state.getBlock(), pos, t.toString());
            return java.util.Collections.emptyList();
        }
    }

    /** Collects the set of blocks Tree Feller would remove (no breaking). */
    public static Set<BlockPos> collect(ServerWorld world, BlockPos startingPos) {
        Set<BlockPos> out = new HashSet<>();

        // We mirror the recursive shape, but drive it iteratively to avoid deep stacks.
        ArrayDeque<BlockPos> futureCenters = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet(128);
        boolean[] reachedThreshold = new boolean[] { false }; // mutable flag

        // Kick off the first center as the starting block (mcMMO starts at the first broken block)
        processTree(startingPos, world, out, futureCenters, seen, reachedThreshold);

        while (!futureCenters.isEmpty() && !reachedThreshold[0]) {
            BlockPos center = futureCenters.removeFirst();
            processTree(center, world, out, futureCenters, seen, reachedThreshold);
        }
        return out;
    }

    // ---------------- core search (ported from mcMMO) ----------------

    private static void processTree(BlockPos center,
                                    ServerWorld world,
                                    Set<BlockPos> treeFellerBlocks,
                                    ArrayDeque<BlockPos> futureCenters,
                                    LongOpenHashSet seen,
                                    boolean[] reachedThreshold) {

        // If there is a log above: trunk mode (flat cylinder at same Y)
        boolean trunk = processTarget(center.up(), world, futureCenters, treeFellerBlocks, seen, reachedThreshold);

        if (trunk) {
            for (int[] d : DIRECTIONS_CYLINDER_R2_NO_CORNERS) {
                if (reachedThreshold[0]) return;
                BlockPos pos = center.add(d[0], 0, d[1]);
                processTarget(pos, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
            }
            return;
        }

        // Branch/top mode:
        // Cover DOWN (explicit)
        if (!reachedThreshold[0]) {
            processTarget(center.down(), world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
        }

        // Search a cube: cylinder at Y-1, Y, Y+1
        for (int dy = -1; dy <= 1 && !reachedThreshold[0]; dy++) {
            for (int[] d : DIRECTIONS_CYLINDER_R2_NO_CORNERS) {
                if (reachedThreshold[0]) return;
                BlockPos pos = center.add(d[0], dy, d[1]);
                processTarget(pos, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
            }
        }
    }

    /**
     * Try to add a block to the removal set and, if it's a log, also enqueue it as a future center.
     * @return true iff the given block is a log not already present.
     */
    private static boolean processTarget(BlockPos pos,
                                         ServerWorld world,
                                         ArrayDeque<BlockPos> futureCenters,
                                         Set<BlockPos> treeFellerBlocks,
                                         LongOpenHashSet seen,
                                         boolean[] reachedThreshold) {

        long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
        if (seen.contains(key)) return false;

        if (PlacedBlockTracker.get(world).isMarked(pos)) return false;

        // Threshold check BEFORE expanding through leaves
        if (treeFellerBlocks.size() > THRESHOLD) {
            reachedThreshold[0] = true;
            return false;
        }

        BlockState state = world.getBlockState(pos);

        if (isLog(state)) {
            seen.add(key);
            treeFellerBlocks.add(pos.toImmutable());
            futureCenters.addLast(pos.toImmutable());
            return true;
        }

        if (INCLUDE_NON_WOOD_PARTS && isTreeNonWood(state)) {
            seen.add(key);
            treeFellerBlocks.add(pos.toImmutable());
            return false;
        }

        return false;
    }

    private static boolean isLog(BlockState s) {
        WoodcuttingConfig cfg = ConfigManager.getSkillConfig(SkillType.WOODCUTTING);
        var blockName = Registries.BLOCK.getId(s.getBlock()).toString();
        return cfg.getBlocks().containsKey(blockName);
    }

    private static boolean isTreeNonWood(BlockState s) {
        // Check tag for leaves, roots, wart blocks, etc.
        return BlockClassifier.isLeaf(s.getBlock())
                || BlockClassifier.isWartBlock(s.getBlock())
                || BlockClassifier.isRoots(s.getBlock());
    }

    // -----------------------------------------------------------------
    // Neighborhood: cylinder of radius ~2 in X/Z, excluding center (0,0) and the 4 corners (±2,±2).
    // Matches mcMMO behavior closely; built once and reused.
    private static final int[][] DIRECTIONS_CYLINDER_R2_NO_CORNERS = buildDirections();

    private static int[][] buildDirections() {
        List<int[]> dirs = new ArrayList<>(24);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;                 // omit center
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // omit corners
                // Keep a roundish cylinder: accept cells with Euclidean radius <= 2.25
                double r2 = dx * dx + dz * dz;
                if (r2 <= 5.0625) dirs.add(new int[]{dx, dz});
            }
        }
        return dirs.toArray(new int[0][]);
    }
}
