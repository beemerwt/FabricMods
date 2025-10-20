package com.github.beemerwt.mcrpg.persistent;

import com.github.beemerwt.mcrpg.McRPG;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;

public final class CropMarkers extends PersistentState {
    private static final String KEY = "mcrpg_herbalism_markers";
    private final Long2FloatOpenHashMap posToMultiplier = new Long2FloatOpenHashMap();

    private record Entry(long pos, float mult) {}
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                    Codec.FLOAT.fieldOf("mult").forGetter(Entry::mult)
            ).apply(inst, Entry::new)
    );

    private static final Codec<CropMarkers> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    ENTRY_CODEC.listOf().fieldOf("entries").forGetter(m -> {
                        var out = new ArrayList<Entry>(m.posToMultiplier.size());
                        for (var it = m.posToMultiplier.long2FloatEntrySet().fastIterator(); it.hasNext();) {
                            var e = it.next();
                            out.add(new Entry(e.getLongKey(), e.getFloatValue()));
                        }
                        return out;
                    })
            ).apply(inst, entries -> {
                CropMarkers m = new CropMarkers();
                for (Entry e : entries) {
                    float f = clampMultiplier(e.mult());
                    if (f > 1.0f) m.posToMultiplier.put(e.pos(), f);
                }
                return m;
            })
    );

    public static final PersistentStateType<CropMarkers> TYPE =
            new PersistentStateType<>(KEY, CropMarkers::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    public CropMarkers() {
        super();
        posToMultiplier.defaultReturnValue(1.0f);
    }

    public static CropMarkers get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    public float getMultiplier(BlockPos pos) {
        return posToMultiplier.get(pos.asLong());
    }

    public boolean isMarked(BlockPos pos) {
        return getMultiplier(pos) > 1.0;
    }

    public void mark(ServerWorld world, BlockPos pos, float multiplier) {
        float clamped = clampMultiplier(multiplier);
        if (clamped <= 1.0f) {
            unmark(world, pos);
            return;
        }
        posToMultiplier.put(pos.asLong(), clamped);
        markDirty();
    }

    public void unmark(ServerWorld world, BlockPos pos) {
        long k = pos.asLong();
        if (posToMultiplier.containsKey(k)) {
            posToMultiplier.remove(k);
            markDirty();
        }
    }

    public void maybeCleanupIfAir(ServerWorld world, BlockPos pos) {
        if (world.isAir(pos)) {
            unmark(world, pos);
        }
    }

    public int size() {
        return posToMultiplier.size();
    }

    public boolean containsKey(long key) {
        return posToMultiplier.containsKey(key);
    }

    private static float clampMultiplier(float f) {
        if (Float.isNaN(f)) {
            McRPG.getLogger().warning("Attempted to set NaN Green Thumb multiplier");
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(f, 16.0f)); // hard cap, just in case
    }
}
