package com.github.beemerwt.telekinesis;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakZones {
    private BreakZones() {}

    private static final class Zone {
        final Vec3d center;
        final double r2;
        final long expiresAt; // server tick time (world.getTime())

        Zone(Vec3d c, double radius, long expiresAt) {
            this.center = c;
            this.r2 = radius * radius;
            this.expiresAt = expiresAt;
        }

        boolean contains(Vec3d p) {
            return p.squaredDistanceTo(center) <= r2;
        }
    }

    // zones per player
    private static final Map<UUID, Deque<Zone>> ZONES = new ConcurrentHashMap<>();

    /** Add a small zone at pos for `lifetimeTicks`. */
    public static void add(UUID playerId, Vec3d center, double radius, long nowTick, int lifetimeTicks) {
        ZONES.computeIfAbsent(playerId, k -> new ArrayDeque<>())
                .addLast(new Zone(center, radius, nowTick + lifetimeTicks));
    }

    /** Add multiple zones (e.g., for a vertical column of “connected” blocks). */
    public static void addAll(UUID playerId, Collection<Vec3d> centers, double radius, long nowTick, int lifetimeTicks) {
        Deque<Zone> dq = ZONES.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        for (Vec3d c : centers) dq.addLast(new Zone(c, radius, nowTick + lifetimeTicks));
    }

    /** Remove expired zones. Call once per server tick. */
    public static void purgeExpired(long nowTick) {
        for (Deque<Zone> dq : ZONES.values()) {
            while (!dq.isEmpty() && dq.peekFirst().expiresAt <= nowTick) {
                dq.removeFirst();
            }
        }
    }

    /** If itemPos is inside any player’s zone, return that player (prefer the one with the most recent/unexpired zone). */
    public static ServerPlayerEntity breakerFor(ServerWorld world, Vec3d itemPos) {
        ServerPlayerEntity best = null;
        long bestExpires = Long.MIN_VALUE;

        for (Map.Entry<UUID, Deque<Zone>> e : ZONES.entrySet()) {
            UUID pid = e.getKey();
            ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(pid);
            if (sp == null || sp.isRemoved() || sp.isDead()) continue;

            // scan zones from the tail (newest first)
            Iterator<Zone> it = e.getValue().descendingIterator();
            while (it.hasNext()) {
                Zone z = it.next();
                if (z.contains(itemPos)) {
                    if (z.expiresAt > bestExpires) {
                        bestExpires = z.expiresAt;
                        best = sp;
                    }
                    break; // we found a match for this player, no need to check older zones
                }
            }
        }
        return best;
    }

    /** Convenience: add a bounded vertical “column” of zones for connected stacks. */
    public static void addColumnZones(ServerWorld world, UUID playerId, BlockPos base, int upMax, int downMax,
                                      int lifetimeTicks, double radius) {
        long now = world.getTime();

        List<Vec3d> centers = new ArrayList<>();
        // upward
        BlockPos.Mutable pos = base.mutableCopy();
        for (int i = 0; i <= upMax; i++) {
            centers.add(Vec3d.ofCenter(pos));
            pos.move(0, 1, 0);
        }
        // downward
        pos.set(base);
        for (int i = 1; i <= downMax; i++) {
            pos.move(0, -1, 0);
            centers.add(Vec3d.ofCenter(pos));
        }
        addAll(playerId, centers, radius, now, lifetimeTicks);
    }
}

