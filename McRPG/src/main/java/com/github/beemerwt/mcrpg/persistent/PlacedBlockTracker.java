package com.github.beemerwt.mcrpg.persistent;

import com.github.beemerwt.events.PlayerEvents;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks player-placed blocks
 */
public final class PlacedBlockTracker extends PersistentState {
    private static final String KEY = "mcrpg_player_placed";
    private final Set<Long> positions = new HashSet<>();
    private final Set<Long> removedThisTick = new HashSet<>();

    // Gross...
    private static final Codec<PlacedBlockTracker> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(Codec.LONG.listOf().fieldOf("positions").forGetter(tracker ->
                    new ArrayList<>(tracker.positions))
            ).apply(inst, (positions) -> {
                PlacedBlockTracker tracker = new PlacedBlockTracker();
                tracker.positions.addAll(positions);
                return tracker;
            })
    );

    public static final PersistentStateType<PlacedBlockTracker> TYPE = new PersistentStateType<>(
            KEY, PlacedBlockTracker::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    public PlacedBlockTracker() {
        super();
    }

    public static void register() {
        PlayerEvents.BLOCK_PLACED.register(PlacedBlockTracker::onBlockPlaced);
        PlayerBlockBreakEvents.AFTER.register(PlacedBlockTracker::onBlockBroken);
        ServerTickEvents.END_WORLD_TICK.register(PlacedBlockTracker::onEndWorldTick);
    }

    private static void onEndWorldTick(ServerWorld world) {
        // Update the tracker with removed blocks
        PlacedBlockTracker tracker = PlacedBlockTracker.get(world);
        if (tracker.removedThisTick.isEmpty()) return;

        for (Long posLong : tracker.removedThisTick) {
            tracker.positions.remove(posLong);
        }

        tracker.removedThisTick.clear();
        tracker.markDirty();
    }

    private static void onBlockBroken(
        World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity entity
    ) {
        if (!(world instanceof ServerWorld sw)) return;

        var block = state.getBlock();
        var blockName = Registries.BLOCK.getId(block).toString();
        McRPG.getLogger().debug("Handling AFTER block break event at {} by player {}", pos, player.getName().getString());

        if (!ConfigManager.isConfiguredBlock(blockName)) {
            McRPG.getLogger().debug("No skill associated with block " + blockName);
            return;
        }

        // Queue block for removal from block tracking
        var tracker = PlacedBlockTracker.get(sw);
        if (!tracker.isMarked(pos)) return;

        tracker.removedThisTick.add(pos.asLong());
        McRPG.getLogger().debug("Flagged block {} for PlacedBlockTracker removal", blockName);
    }

    private static void onBlockPlaced(
        World world, @Nullable LivingEntity placer, BlockPos pos, BlockState state, ItemStack stack
    ) {
        if (!(world instanceof ServerWorld sw)) return;
        var block = state.getBlock();

        // Only track placements that are relevant to a skill (reduces save size)
        String id = Registries.BLOCK.getId(block).toString();
        if (!ConfigManager.isConfiguredBlock(id)) {
            McRPG.getLogger().debug("Placed block {} is not tracked by any skill.", id);
            return;
        }

        PlacedBlockTracker.get(sw).mark(pos);
    }

    public static PlacedBlockTracker get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    public boolean isMarked(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void mark(BlockPos pos) {
        if (positions.add(pos.asLong()))
            markDirty();
    }

    public void unmark(BlockPos pos) {
        if (positions.remove(pos.asLong()))
            markDirty();
    }
}

