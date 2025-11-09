package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.TreeFellerConfig;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.service.XpModifier;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.*;
import java.util.function.Supplier;

/**
 * McRPG Tree Feller — adaptation of mcMMO's original Tree Feller search.
 * <p>
 * Algorithm:
 * - If the block above the current center is a log: search a flat "cylinder" neighborhood
 * (a disk of radius ~2 in X/Z) at the same Y.
 * - Otherwise (branch/top): search the same cylinder at Y-1..Y..Y+1
 * and also check the block directly below.
 * - For any log found, add it to the removal set and schedule it as a new center to recurse.
 * - For tree non-wood parts (leaves, roots, wart blocks, etc.), add to removal but don't recurse.
 * <p>
 * Safety:
 * - Hard threshold on total blocks to avoid nuking forests.
 * - Optional "ineligible" predicate so you can skip player-placed logs if you track them.
 */
public final class TreeFeller extends SuperAbility<WoodcuttingConfig, TreeFellerConfig> {
    private static final int THRESHOLD = 1024;
    private static final boolean INCLUDE_NON_WOOD_PARTS = true;

    private static final Map<UUID, Integer> DEPTH = Collections.synchronizedMap(new WeakHashMap<>());


    public TreeFeller(Supplier<TreeFellerConfig> aConfigSupplier) {
        super(SkillType.WOODCUTTING, aConfigSupplier);
        EventBus.on(BreakBlockEvent.class, this::onBlockBreak);
    }

    static boolean isGuarded(ServerPlayerEntity player) {
        Integer d = DEPTH.get(player.getUuid());
        return d != null && d > 0;
    }

    static void runGuarded(ServerPlayerEntity player, Runnable r) {
        DEPTH.merge(player.getUuid(), 1, Integer::sum);
        try {
            r.run();
        } finally {
            DEPTH.compute(player.getUuid(), (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    /**
     * Collects the tree starting at 'start' and processes each block:
     * - precomputes drops for Double Drops
     * - breaks the block
     * - damages the axe on logs
     * - awards per-block XP (doubled if Double Drops procs)
     * Returns the total (pre-modifier) XP earned.
     */
    private void onBlockBreak(BreakBlockEvent e) {
        var player = e.player();

        // prevent re-entrance
        if (isGuarded(player)) return;

        if (runtime.getState(player) != SuperAbilityRuntime.State.ACTIVE) return;

        var tool = e.tool();
        if (!ItemClassifier.isAxe(tool.getItem())) return;

        var block = e.block();
        var blockName = Registries.BLOCK.getId(block).toString();
        if (!skillConfig.getBlocks().containsKey(blockName)) return;

        // Resolve XP for starting block (skip if non-xp)
        long xp = Leveling.resolveBlockXp(skillConfig.getBlocks(), block);
        if (xp <= 0) return;

        var world = e.world();
        var start = e.pos();

        var treeBlocks = collect(world, start, block);
        if (treeBlocks.isEmpty()) return;

        var ordered = treeBlocks.stream()
            .sorted(Comparator.comparingInt(Vec3i::getY))
            .toList();

        runGuarded(player, () -> ordered.forEach(player.interactionManager::tryBreakBlock));

        McRPG.getLogger().debug("Tree Feller felled {} blocks for {}",
            ordered.size(), player.getName().getString());
    }

    /**
     * Collects the set of blocks Tree Feller would remove (no breaking).
     */
    public Set<BlockPos> collect(ServerWorld world, BlockPos startingPos, Block startingBlock) {
        Set<BlockPos> out = new HashSet<>();

        // TODO: Match only to startingBlock and leaves of the same tree type

        // We mirror the recursive shape, but drive it iteratively to avoid deep stacks.
        ArrayDeque<BlockPos> futureCenters = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet(128);
        boolean[] reachedThreshold = new boolean[]{false}; // mutable flag

        // Kick off the first center as the starting block (mcMMO starts at the first broken block)
        processTree(startingPos, startingBlock, world, out, futureCenters, seen, reachedThreshold);

        while (!futureCenters.isEmpty() && !reachedThreshold[0]) {
            BlockPos center = futureCenters.removeFirst();
            processTree(center, startingBlock, world, out, futureCenters, seen, reachedThreshold);
        }
        return out;
    }

    // ---------------- core search (ported from mcMMO) ----------------

    private void processTree(BlockPos center,
                             Block startingBlock,
                             ServerWorld world,
                             Set<BlockPos> treeFellerBlocks,
                             ArrayDeque<BlockPos> futureCenters,
                             LongOpenHashSet seen,
                             boolean[] reachedThreshold) {

        // If there is a log above: trunk mode (flat cylinder at same Y)
        boolean trunk = processTarget(center.up(), startingBlock, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);

        if (trunk) {
            for (int[] d : DIRECTIONS_CYLINDER_R2_NO_CORNERS) {
                if (reachedThreshold[0]) return;
                BlockPos pos = center.add(d[0], 0, d[1]);
                processTarget(pos, startingBlock, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
            }
            return;
        }

        // Branch/top mode:
        // Cover DOWN (explicit)
        if (!reachedThreshold[0]) {
            processTarget(center.down(), startingBlock, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
        }

        // Search a cube: cylinder at Y-1, Y, Y+1
        for (int dy = -1; dy <= 1 && !reachedThreshold[0]; dy++) {
            for (int[] d : DIRECTIONS_CYLINDER_R2_NO_CORNERS) {
                if (reachedThreshold[0]) return;
                BlockPos pos = center.add(d[0], dy, d[1]);
                processTarget(pos, startingBlock, world, futureCenters, treeFellerBlocks, seen, reachedThreshold);
            }
        }
    }

    /**
     * Try to add a block to the removal set and, if it's a log, also enqueue it as a future center.
     *
     * @return true iff the given block is a log not already present.
     */
    private boolean processTarget(BlockPos pos,
                                  Block startingBlock,
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

        if (state.getBlock().equals(startingBlock) && isLog(state)) {
            seen.add(key);
            treeFellerBlocks.add(pos.toImmutable());
            futureCenters.addLast(pos.toImmutable());
            return true;
        }

        if (INCLUDE_NON_WOOD_PARTS && isStateNonWood(state)) {
            seen.add(key);
            treeFellerBlocks.add(pos.toImmutable());
            return false;
        }

        return false;
    }

    private boolean isLog(BlockState s) {
        var blockName = Registries.BLOCK.getId(s.getBlock()).toString();
        return this.skillConfig.getBlocks().containsKey(blockName);
    }

    private boolean isStateNonWood(BlockState s) {
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

    @Override
    protected String id() {
        return "mcrpg.ability.tree_feller";
    }

    @Override
    protected String toolId() {
        return "mcrpg.tool.axe";
    }

    @Override
    protected boolean canReady(ServerPlayerEntity p) {
        return ItemClassifier.isAxe(p.getMainHandStack().getItem());
    }
}
