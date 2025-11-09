package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.GreenTerraConfig;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.util.TickClock;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// TODO: Particle effect and/or sound on replant

public final class GreenTerra extends SuperAbility<HerbalismConfig, GreenTerraConfig> {
    @Override protected String id() { return "mcrpg.ability.green_terra"; }
    @Override protected String toolId() { return "mcrpg.tool.hoe"; }

    private record ReplantCandidate(BlockPos pos, BlockState seedling) { }

    private static final Map<UUID, ArrayDeque<ReplantCandidate>> QUEUE = new ConcurrentHashMap<>();
    // New: track who already has a pending flush scheduled
    private static final Set<UUID> SCHEDULED = ConcurrentHashMap.newKeySet();

    private static final int FLUSH_DELAY_TICKS = 40; // ~2s @20tps

    public GreenTerra(Supplier<GreenTerraConfig> aConfigSupplier) {
        super(SkillType.HERBALISM, aConfigSupplier);
        EventBus.subscribe(BreakBlockEvent.class, this::onBreakBlock);
    }

    @Override
    protected boolean canReady(ServerPlayerEntity p) {
        return ItemClassifier.isHoe(p.getMainHandStack().getItem());
    }

    @Override
    boolean onActivate(AttackBlockEvent e) {
        if (super.onActivate(e)) {
            QUEUE.put(e.player().getUuid(), new ArrayDeque<>());
            return true;
        }

        return false;
    }

    @Override
    void onExpire(ServerPlayerEntity p) {
        super.onExpire(p);
        // Full/last flush of remaining items
        flushNow(p, p.getEntityWorld(), true);
        // Clean up queue
        QUEUE.remove(p.getUuid());
        SCHEDULED.remove(p.getUuid());
    }

    private void onBreakBlock(BreakBlockEvent e) {
        var player = e.player();
        if (runtime.getState(player) != SuperAbilityRuntime.State.ACTIVE) return;
        if (!ItemClassifier.isHoe(e.tool().getItem())) return;

        var q = QUEUE.get(player.getUuid());
        if (q == null) return; // not active

        for (var r : REPLANTERS) {
            var seed = r.seedling(e.world(), e.pos(), e.state());
            if (seed.isPresent()) {
                q.add(new ReplantCandidate(e.pos().toImmutable(), seed.get()));
                // schedule a delayed flush if not already scheduled
                scheduleFlush(player.getUuid(), e.world());
                return;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------------

    private void scheduleFlush(UUID playerId, ServerWorld world) {
        if (!SCHEDULED.add(playerId)) return; // already scheduled

        // Use your TickScheduler; this runs on server thread
        TickClock.schedule(FLUSH_DELAY_TICKS, () -> {
            try {
                // Resolve player at run time (handles dimension changes / respawns)
                var server = world.getServer();
                var player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    // partial flush (respect PER_FLUSH_LIMIT)
                    flushNow(player, world, false);
                }
            } finally {
                SCHEDULED.remove(playerId);
                // If there’s still work, chain another delayed flush
                var q = QUEUE.get(playerId);
                if (q != null && !q.isEmpty()) {
                    // re-schedule to spread work
                    scheduleFlush(playerId, world);
                }
            }
        });
    }

    // ------------------------------------------------------------------------
    // Flushing (partial or full)
    // ------------------------------------------------------------------------

    private void flushNow(ServerPlayerEntity player, ServerWorld world, boolean full) {
        var q = QUEUE.get(player.getUuid());
        if (q == null || q.isEmpty()) return;

        var cm = world.getChunkManager();
        var rng = player.getRandom();

        // Chance is based on current level at time of flush
        int lvl = Leveling.getLevel(player, SkillType.HERBALISM);
        double chance = Leveling.getScaledPercentage(config.baseReplantChance, config.maxReplantChance, lvl);

        int replanted = 0;

        while (!q.isEmpty()) {
            var c = q.pollFirst();
            var pos = c.pos();

            // chunk check: be sure to use chunk coords or pos overload
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (!cm.isChunkLoaded(cx, cz)) continue;
            if (rng.nextDouble() > chance) continue;

            var cur = world.getBlockState(pos);
            if (!(cur.isAir() || cur.isReplaceable())) continue;

            if (!canPlaceSeedling(world, pos, c.seedling())) continue;

            if (world.setBlockState(pos, c.seedling(), Block.NOTIFY_ALL)) {
                replanted++;
            }
        }

        if (replanted > 0) {
            McRPG.getLogger().debug("Green Terra replanted {} crops for {}",
                    replanted, player.getName().getString());
        }
    }

    // ------------------------------------------------------------------------
    // Replanter pipeline
    // ------------------------------------------------------------------------

    private interface Replanter {
        Optional<BlockState> seedling(ServerWorld world, BlockPos pos, BlockState brokenState);
    }

    private static final List<Replanter> REPLANTERS = List.of(
            // 1) Vanilla farmland crops: wheat, carrots, potatoes, beetroot, torchflower crop, etc.
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof CropBlock)) return Optional.empty();
                if (!isFarmland(world, pos.down())) return Optional.empty();

                IntProperty age = Growth.getAgeProperty(state);
                if (age == null) return Optional.empty();

                int minAge = age.getValues().stream().min(Integer::compare).orElse(0);
                return Optional.of(state.with(age, minAge));
            },

            // 2) Nether wart on soul sand
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof NetherWartBlock)) return Optional.empty();
                if (!isSoulSand(world, pos.down())) return Optional.empty();
                return Optional.of(state.with(NetherWartBlock.AGE, 0));
            },

            // 3) Cocoa: keep facing, reset age; requires jungle log behind the pod
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof CocoaBlock)) return Optional.empty();

                EnumProperty<Direction> facing = CocoaBlock.FACING;
                IntProperty age = CocoaBlock.AGE;
                if (!state.contains(facing) || !state.contains(age)) return Optional.empty();

                Direction face = state.get(facing);
                BlockPos support = pos.offset(face.getOpposite());
                if (!isJungleLog(world, support)) return Optional.empty();

                return Optional.of(state.with(age, 0));
            },

            // 4) Sweet berry bush: reset age; requires sturdy ground below
            (world, pos, state) -> {
                if (!(state.getBlock() instanceof SweetBerryBushBlock)) return Optional.empty();
                IntProperty age = SweetBerryBushBlock.AGE;
                if (!state.contains(age)) return Optional.empty();
                if (!isSolidBelow(world, pos)) return Optional.empty();

                return Optional.of(state.with(age, 0));
            },

            // 5) Generic AGE fallback for other plant-like blocks (mods, etc.)
            (world, pos, state) -> {
                if (!looksPlantLike(state.getBlock())) return Optional.empty();

                IntProperty age = Growth.getAgeProperty(state);
                if (age == null) return Optional.empty();

                // Only replant if below block is at least sturdy; customize as needed.
                if (!isSolidBelow(world, pos)) return Optional.empty();

                int minAge = age.getValues().stream().min(Integer::compare).orElse(0);
                return Optional.of(state.with(age, minAge));
            }
    );

    // ------------------------------------------------------------------------
    // Placement sanity checks
    // ------------------------------------------------------------------------
    private static boolean canPlaceSeedling(ServerWorld world, BlockPos pos, BlockState seedling) {
        Block b = seedling.getBlock();

        if (b instanceof CropBlock)
            return isFarmland(world, pos.down());

        if (b instanceof NetherWartBlock)
            return isSoulSand(world, pos.down());

        if (b instanceof CocoaBlock) {
            EnumProperty<Direction> facing = CocoaBlock.FACING;
            if (!seedling.contains(facing)) return false;
            Direction face = seedling.get(facing);
            BlockPos support = pos.offset(face.getOpposite());
            return isJungleLog(world, support);
        }

        // Fallback: require sturdy block below
        return isSolidBelow(world, pos);
    }

    // ------------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------------

    private static boolean isFarmland(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof FarmlandBlock;
    }

    private static boolean isSoulSand(ServerWorld w, BlockPos pos) {
        return w.getBlockState(pos).getBlock() instanceof SoulSandBlock;
    }

    private static boolean isJungleLog(ServerWorld w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        return b == Blocks.JUNGLE_LOG || b == Blocks.STRIPPED_JUNGLE_LOG;
    }

    private static boolean isSolidBelow(ServerWorld w, BlockPos pos) {
        BlockPos below = pos.down();
        return w.getBlockState(below).isSolidBlock(w, below);
    }

    private static boolean looksPlantLike(Block b) {
        return b instanceof PlantBlock || b instanceof CocoaBlock;
    }
}
