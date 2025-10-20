package com.github.beemerwt.mcrpg.persistent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.*;

/**
 * Persistent ownership for furnace slots at a given BlockPos.
 * Fields:
 *  - input owner (slot 0)
 *  - fuel owner  (slot 1)
 * UUIDs are saved as strings via Codec.STRING.optionalFieldOf(...).
 */
public class FurnaceSlotOwners extends PersistentState {
    public static final class Owners {
        public UUID input;   // slot 0
        public UUID fuel;    // slot 1

        public Owners() {}

        public Owners(UUID all) {
            this.input = all;
            this.fuel = all;
        }

        public Owners(UUID input, UUID fuel) {
            this.input = input;
            this.fuel = fuel;
        }

        public boolean isAllNull() {
            return input == null && fuel == null;
        }
    }

    private static final String KEY = "mcrpg_furnace_slot_owners";

    private static final class Entry {
        private final BlockPos pos;
        private final UUID inputOwner;
        private final UUID fuelOwner;

        public Entry(BlockPos pos, UUID inputOwner, UUID fuelOwner) {
            this.pos = pos;
            this.inputOwner = inputOwner;
            this.fuelOwner = fuelOwner;
        }

        public Optional<String> getInputOwner() {
            return Optional.ofNullable(inputOwner).map(UUID::toString);
        }

        public Optional<String> getFuelOwner() {
            return Optional.ofNullable(fuelOwner).map(UUID::toString);
        }
    }

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(e -> e.pos),

            Codec.STRING.optionalFieldOf("input").xmap(
                    FurnaceSlotOwners::toUuidOpt,
                    FurnaceSlotOwners::toStringOpt
            ).forGetter(e -> Optional.ofNullable(e.inputOwner)),

            Codec.STRING.optionalFieldOf("fuel").xmap(
                    FurnaceSlotOwners::toUuidOpt,
                    FurnaceSlotOwners::toStringOpt
            ).forGetter(e -> Optional.ofNullable(e.fuelOwner))
        ).apply(inst, (blockPos, input, fuel) ->
            new Entry(blockPos, input.orElse(null), fuel.orElse(null))
        ));

    private static final Codec<List<Entry>> ENTRIES_CODEC = ENTRY_CODEC.listOf();

    private static final Codec<FurnaceSlotOwners> CODEC = ENTRIES_CODEC.xmap(
            list -> {
                FurnaceSlotOwners f = new FurnaceSlotOwners();
                for (Entry e : list) f.put(e.pos, e.inputOwner, e.fuelOwner);
                return f;
            },
            f -> {
                List<Entry> out = new ArrayList<>(f.byPos.size());
                for (var it = f.byPos.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
                    var kv = it.next();
                    BlockPos pos = BlockPos.fromLong(kv.getLongKey());
                    var owners = kv.getValue();
                    out.add(new Entry(pos, owners.input, owners.fuel));
                }
                return out;
            }
    );

    private final Long2ObjectOpenHashMap<Owners> byPos = new Long2ObjectOpenHashMap<>();
    public static final PersistentStateType<FurnaceSlotOwners> TYPE =
            new PersistentStateType<>(KEY, FurnaceSlotOwners::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    // ---------------------------
    // PersistentState lifecycle
    // ---------------------------

    public FurnaceSlotOwners() {
        super();
    }

    public static FurnaceSlotOwners get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();
        return psm.getOrCreate(TYPE);
    }

    // ---------------------------
    // Convenience API
    // ---------------------------

    public Owners get(BlockPos pos) {
        return byPos.get(pos.asLong());
    }

    public Owners getOrCreate(BlockPos pos) {
        Owners val = byPos.get(pos.asLong());
        if (val == null) {
            val = new Owners();
            byPos.put(pos.asLong(), val);
            markDirty();
        }
        return val;
    }

    public void setAll(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        byPos.put(pos.asLong(), new Owners(owner));
        markDirty();
    }

    public void setInput(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        getOrCreate(pos).input = owner;
        markDirty();
    }

    public void setFuel(BlockPos pos, UUID owner) {
        Objects.requireNonNull(owner, "owner");
        getOrCreate(pos).fuel = owner;
        markDirty();
    }

    public UUID getInputOwner(BlockPos pos) {
        Owners o = byPos.get(pos.asLong());
        return o == null ? null : o.input;
    }

    public UUID getFuelOwner(BlockPos pos) {
        Owners o = byPos.get(pos.asLong());
        return o == null ? null : o.fuel;
    }

    public void remove(BlockPos pos) {
        if (byPos.remove(pos.asLong()) != null) {
            markDirty();
        }
    }

    public boolean isEmpty() {
        return byPos.isEmpty();
    }

    private void put(BlockPos pos, UUID input, UUID fuel) {
        byPos.put(pos.asLong(), new Owners(input, fuel));
    }

    // Helpers
    private static Optional<UUID> toUuidOpt(Optional<String> s) {
        if (s.isEmpty()) return Optional.empty();
        var str = s.get();
        if (str.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(str));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> toStringOpt(Optional<UUID> u) {
        return u.map(UUID::toString);
    }
}
