package com.github.beemerwt.telekinesis;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.BambooFeature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakZones {
    private BreakZones() {}

    /* ==============================
       Tunables (config-ready later)
       ============================== */
    private static final double DEFAULT_RADIUS = 2.25;
    private static final double RADIUS_SQ      = DEFAULT_RADIUS * DEFAULT_RADIUS;
    private static final int    MOB_LINGER_TTL = 10;     // ticks
    private static final int    MOB_LINGER_CAP = 8;      // max zones kept per player

    // Add near your other constants
    private static final int COLUMN_CAP   = 256; // safety bound
    private static final double COLUMN_XZ_EPS  = 0.75; // how close an item must be to the column center in X/Z

    /* ==============================
       Block-break session (same tick)
       ============================== */

    private static final class Session {
        final UUID playerId;
        final ServerWorld world;
        final Vec3d center;
        final ArrayList<ItemStack> items = new ArrayList<>();
        int xp;

        Session(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
            this.playerId = p.getUuid();
            this.world = w;
            this.center = Vec3d.ofCenter(pos);
        }

        boolean inRange(Vec3d p) { return p.squaredDistanceTo(center) <= RADIUS_SQ; }

        void addItem(ItemStack stack) {
            // merge into existing buffered stacks to reduce churn
            for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
                ItemStack s = items.get(i);
                if (TeleContext.canCombine(s, stack)) {
                    int free = s.getMaxCount() - s.getCount();
                    if (free > 0) {
                        int move = Math.min(free, stack.getCount());
                        s.increment(move);
                        stack.decrement(move);
                    }
                }
            }
            if (!stack.isEmpty()) items.add(stack.copy());
        }

        boolean tryBufferItem(ServerWorld w, ItemEntity item) {
            if (this.world != w) return false;
            Vec3d p = item.getEntityPos();
            if (!inRange(p)) return false;
            addItem(item.getStack());
            return true;
        }

        boolean tryBufferXp(ServerWorld w, ExperienceOrbEntity orb) {
            if (this.world != w) return false;
            Vec3d p = orb.getEntityPos();
            if (!inRange(p)) return false;
            // getValue() ⇆ getValue() depending on mappings
            this.xp += orb.getValue();
            return true;
        }
    }

    /**
     * Linger after commit (same tick only). One per player is sufficient.
     */
    private static class Linger {
        final UUID playerId;
        final ServerWorld world;
        final Vec3d center;

        private Linger(ServerPlayerEntity player, ServerWorld world, Vec3d center) {
            this.playerId = player.getUuid();
            this.world = world;
            this.center = center;
        }

        boolean inRange(Vec3d p) {
            return p.squaredDistanceTo(center) <= RADIUS_SQ;
        }
    }

    /* ==============================
       Mob-kill linger (TTL across ticks)
       ============================== */

    private static final class MobLinger extends Linger {
        final double radiusSq;
        int ttl; // ticks remaining

        MobLinger(ServerPlayerEntity p, ServerWorld w, Vec3d c, double radius, int ttl) {
            super(p, w, c);
            this.ttl = ttl;
            this.radiusSq = radius * radius;
        }

        @Override
        boolean inRange(Vec3d p) { return p.squaredDistanceTo(center) <= radiusSq; }
    }

    // Add alongside your inner classes
    private static final class ColumnQuota {
        final UUID playerId;
        final ServerWorld world;
        final int x, z;         // column anchor (X/Z of the broken block)
        final int minY;         // items at or above this Y are considered "from the stack above"
        final Item expectedItem; // block.asItem() for matching
        int remaining;          // how many blocks above were counted
        int ttl;                // ticks left before we give up

        ColumnQuota(ServerPlayerEntity p, ServerWorld w, BlockPos base, Block baseBlock, int aboveCount, int ttl) {
            this.playerId = p.getUuid();
            this.world = w;
            this.x = base.getX();
            this.z = base.getZ();
            this.minY = base.getY();
            this.expectedItem = baseBlock.asItem();
            this.remaining = aboveCount;
            this.ttl = ttl;
        }

        boolean matches(ItemEntity item) {
            if (item.getEntityWorld() != world) return false;
            var pos = item.getEntityPos();
            if (pos.y < this.minY) return false;
            // constrain to column center in X/Z (items can drift a bit)
            if (Math.abs(pos.x - (x + 0.5)) > COLUMN_XZ_EPS) return false;
            if (Math.abs(pos.z - (z + 0.5)) > COLUMN_XZ_EPS) return false;
            return item.getStack().isOf(this.expectedItem);
        }
    }

    /* ==============================
       Storage
       ============================== */

    // One active block-break session (same tick) and linger per player
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Linger>  LINGERS  = new ConcurrentHashMap<>();

    // Mob lingers can stack; newest-most-relevant at the tail.
    private static final Map<UUID, Deque<MobLinger>> MOB_LINGERS = new ConcurrentHashMap<>();

    private static final Map<UUID, Deque<ColumnQuota>> COLUMN_QUOTAS = new ConcurrentHashMap<>();

    /* ==============================
       Fabric event adapters (blocks)
       ============================== */

    public static void onBefore(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        SESSIONS.put(player.getUuid(), new Session(player, world, pos));

        BlockState state = world.getBlockState(pos);
        var block = state.getBlock();

        if (isVerticalChain(block)) {
            int above = countSameBlockAbove(world, pos, block);
            if (above > 0) {
                Deque<ColumnQuota> dq = COLUMN_QUOTAS.computeIfAbsent(player.getUuid(), __ -> new ArrayDeque<>());
                dq.addLast(new ColumnQuota(player, world, pos, block, above, above + 4)); // +4 room to breathe ticks
                while (dq.size() > COLUMN_CAP) dq.removeFirst();
            }
        }
    }

    public static void onAfter(ServerPlayerEntity player) {
        finishSession(player, true);
    }

    public static void onCancel(ServerPlayerEntity player) {
        finishSession(player, false);
    }

    /* ==============================
       Buffer / Linger capture (blocks)
       ============================== */

    public static boolean bufferIfActive(ServerWorld world, ItemEntity item) {
        for (Session s : SESSIONS.values()) {
            if (s.tryBufferItem(world, item)) return true;
        }
        return false;
    }

    public static boolean bufferXpIfActive(ServerWorld world, ExperienceOrbEntity orb) {
        for (Session s : SESSIONS.values()) {
            if (s.tryBufferXp(world, orb)) return true;
        }
        return false;
    }

    public static boolean tryLingerCapture(ServerWorld world, ItemEntity item) {
        Vec3d p = item.getEntityPos();
        for (Linger g : LINGERS.values()) {
            if (g.world == world && g.inRange(p)) {
                ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(g.playerId);
                if (sp != null && !sp.isRemoved() && !sp.isDead()) {
                    return TeleContext.vacuumTo(item, sp);
                }
            }
        }
        return false;
    }

    public static boolean tryLingerCreditXp(ServerWorld world, ExperienceOrbEntity orb) {
        Vec3d p = orb.getEntityPos();
        for (Linger g : LINGERS.values()) {
            if (g.world == world && g.inRange(p)) {
                ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(g.playerId);
                if (sp != null && !sp.isRemoved() && !sp.isDead()) {
                    sp.addExperience(orb.getValue());
                    return true;
                }
            }
        }
        return false;
    }

    /* ==============================
       Mob-kill linger utils (DRY scan)
       ============================== */

    @FunctionalInterface private interface ZoneTest<Z> { boolean test(Z z); }
    @FunctionalInterface private interface ZoneAct<Z>  { boolean act(Z z); } // return true to stop

    private static <Z> boolean scanNewestFirst(Map<UUID, Deque<Z>> map,
                                               ZoneTest<Z> inWorldAndRange,
                                               ZoneAct<Z>  perform) {
        for (Deque<Z> dq : map.values()) {
            if (dq.isEmpty()) continue;
            for (var it = dq.descendingIterator(); it.hasNext(); ) {
                Z z = it.next();
                if (!inWorldAndRange.test(z)) continue;
                if (perform.act(z)) return true;
            }
        }
        return false;
    }

    /* ==============================
       Mob-kill linger add & capture
       ============================== */

    public static void addMobKillZone(ServerPlayerEntity killer, ServerWorld world, Vec3d at) {
        Deque<MobLinger> dq = MOB_LINGERS.computeIfAbsent(killer.getUuid(), __ -> new ArrayDeque<>());
        dq.addLast(new MobLinger(killer, world, at, DEFAULT_RADIUS, MOB_LINGER_TTL));
        while (dq.size() > MOB_LINGER_CAP) dq.removeFirst();
    }

    public static boolean tryMobLingerCapture(ServerWorld world, ItemEntity item) {
        final Vec3d p = item.getEntityPos();
        return scanNewestFirst(MOB_LINGERS,
                z -> z.world == world && z.inRange(p),
                z -> {
                    ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(z.playerId);
                    return sp != null && !sp.isRemoved() && !sp.isDead() && TeleContext.vacuumTo(item, sp);
                });
    }

    public static boolean tryMobLingerCreditXp(ServerWorld world, ExperienceOrbEntity orb) {
        final Vec3d p = orb.getEntityPos();
        return scanNewestFirst(MOB_LINGERS,
                z -> z.world == world && z.inRange(p),
                z -> {
                    ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(z.playerId);
                    if (sp == null || sp.isRemoved() || sp.isDead()) return false;
                    sp.addExperience(orb.getValue());
                    return true;
                });
    }

    // Try capture items/xp from block lingers (new)
    public static boolean tryColumnCapture(ServerWorld world, ItemEntity item) {
        return scanNewestFirst(COLUMN_QUOTAS,
            q -> q.world == world && q.remaining > 0 && q.matches(item),
            q -> {
            ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(q.playerId);
                if (sp == null || sp.isRemoved() || sp.isDead()) return false;

                // Decrement remaining by how many "blocks worth" we just caught.
                // For bamboo/sugar_cane/cactus/etc. one item == one block broken.
                int movedCount = item.getStack().getCount();

                if (TeleContext.vacuumTo(item, sp)) {
                    q.remaining -= Math.max(1, movedCount); // conservative: at least 1
                    if (q.remaining <= 0) {
                        q.ttl = 0;
                    }
                    return true;
                }

                return false;
            });
    }

    /* ==============================
       Lifecycle
       ============================== */

    private static void finishSession(ServerPlayerEntity player, boolean success) {
        Session s = SESSIONS.remove(player.getUuid());
        if (s == null) return;
        if (!success) return;

        TeleContext.giveStacksOrDrop(player, s.items, s.center);
        TeleContext.creditXp(player, s.xp);

        // establishes same-tick linger
        LINGERS.put(player.getUuid(), new Linger(player, s.world, s.center));
    }

    /** Called from ServerTickEvents.END_SERVER_TICK */
    public static void endOfTickCleanup() {
        // Block-break: strictly one tick
        SESSIONS.clear();
        LINGERS.clear();

        COLUMN_QUOTAS.values().removeIf(deque -> {
            deque.removeIf(q -> (--q.ttl) <= 0 || q.remaining <= 0);
            return deque.isEmpty();
        });

        // Mob lingers: TTL countdown
        MOB_LINGERS.values().removeIf(deque -> {
            deque.removeIf(z -> (--z.ttl) <= 0);
            return deque.isEmpty();
        });
    }

    /* ==============================
       Helpers
       ============================== */

    private static boolean isVerticalChain(Block b) {
        return b.equals(Blocks.BAMBOO)
            || b.equals(Blocks.SCAFFOLDING)
            || b.equals(Blocks.SUGAR_CANE)
            || b.equals(Blocks.CACTUS)
            || b.equals(Blocks.KELP) || b.equals(Blocks.KELP_PLANT)
            || b.equals(Blocks.CHORUS_FLOWER) || b.equals(Blocks.CHORUS_PLANT);
        // Add others you care about (chorus? rods?) as needed.
    }

    private static int countSameBlockAbove(ServerWorld world, BlockPos pos, Block same) {
        int count = 0;
        BlockPos.Mutable m = pos.mutableCopy();
        for (int i = 1; i <= COLUMN_CAP; i++) {
            m.set(pos.getX(), pos.getY() + i, pos.getZ());
            BlockState st = world.getBlockState(m);
            if (st.isOf(same)) {
                count++;
                continue;
            }
            break;
        }
        return count;
    }

    /** “Who’s responsible” convenience for your legacy proximity fallback. */
    public static ServerPlayerEntity breakerFor(ServerWorld world, Vec3d pos) {
        for (Session s : SESSIONS.values()) {
            if (s.world == world && s.inRange(pos)) {
                return world.getServer().getPlayerManager().getPlayer(s.playerId);
            }
        }
        Linger g = LINGERS.values().stream()
                .filter(z -> z.world == world && z.inRange(pos))
                .findFirst().orElse(null);
        if (g != null) return world.getServer().getPlayerManager().getPlayer(g.playerId);
        // mob lingers (prefer newest)
        for (Deque<MobLinger> dq : MOB_LINGERS.values()) {
            for (var it = dq.descendingIterator(); it.hasNext(); ) {
                MobLinger z = it.next();
                if (z.world == world && z.inRange(pos)) {
                    return world.getServer().getPlayerManager().getPlayer(z.playerId);
                }
            }
        }
        return null;
    }
}
