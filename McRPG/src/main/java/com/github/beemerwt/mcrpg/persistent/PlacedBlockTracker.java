package com.github.beemerwt.mcrpg.persistent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class PlacedBlockTracker extends PersistentState {
    private static final String KEY = "mcrpg_player_placed";
    private final Set<Long> positions = new HashSet<>();

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

    public static PlacedBlockTracker get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    public boolean isMarked(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void mark(ServerWorld world, BlockPos pos) {
        if (positions.add(pos.asLong())) {
            markDirty();
        }
    }

    public void unmark(ServerWorld world, BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            markDirty();
        }
    }
}

