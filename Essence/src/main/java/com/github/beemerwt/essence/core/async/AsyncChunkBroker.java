package com.github.beemerwt.essence.core.async;

import com.github.beemerwt.essence.core.Essence;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AsyncChunkBroker {
    public static final class Handle {
        private volatile boolean cancelled;
        public void cancel() { cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    private static final class Request {
        final UUID requester;                 // player UUID or null
        final ServerWorld world;
        final ChunkPos pos;
        final Duration ttl;
        final Consumer<Chunk> onReady;   // runs on main thread
        final Runnable onTimeout;            // runs on main thread
        final Handle handle;
        final Instant enqueuedAt = Instant.now();

        Request(UUID requester, ServerWorld world, ChunkPos pos, Duration ttl,
                Consumer<Chunk> onReady, Runnable onTimeout, Handle handle) {
            this.requester = requester;
            this.world = world;
            this.pos = pos;
            this.ttl = ttl;
            this.onReady = onReady;
            this.onTimeout = onTimeout;
            this.handle = handle;
        }
    }

    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static final Long2ObjectOpenHashMap<Request> IN_FLIGHT = new Long2ObjectOpenHashMap<>();
    private static int MAX_CONCURRENT  = 8;  // throttle so you don’t flood IO

    public static void reset() {
        IN_FLIGHT.values().forEach(AsyncChunkBroker::unforce);
        IN_FLIGHT.clear();
        QUEUE.clear();
    }

    /** Adjust concurrency if you like */
    public static void setMaxConcurrentLoads(int max) { MAX_CONCURRENT = Math.max(1, max); }

    /** Call from commands or anywhere on the main thread. Returns a cancelable handle. */
    public static Handle enqueue(UUID requester, ServerWorld world, ChunkPos pos, Duration ttl,
                          Consumer<Chunk> onReady, Runnable onTimeout) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(pos);
        Objects.requireNonNull(ttl);
        Objects.requireNonNull(onReady);

        var h = new Handle();
        QUEUE.addLast(new Request(requester, world, pos, ttl, onReady, onTimeout, h));
        // Kick the pump this tick
        pump();
        return h;
    }

    /** Pump should be invoked every tick from a server tick hook. */
    public static void pump() {
        var server = Essence.getServer();

        // Finish any timeouts
        var now = Instant.now();
        IN_FLIGHT.values().removeIf(req -> {
            if (req.handle.isCancelled()) {
                unforce(req);
                return true;
            }
            if (Duration.between(req.enqueuedAt, now).compareTo(req.ttl) > 0) {
                // timeout -> main thread (we’re already on it)
                if (req.onTimeout != null) req.onTimeout.run();
                unforce(req);
                return true;
            }
            return false;
        });

        // Launch new loads while under the concurrency cap
        while (IN_FLIGHT.size() < MAX_CONCURRENT && !QUEUE.isEmpty()) {
            var req = QUEUE.removeFirst();
            if (req.handle.isCancelled()) continue;

            var cm = req.world.getChunkManager();
            // Keep it around for the entire lifecycle
            req.world.setChunkForced(req.pos.x, req.pos.z, true);

            // Ask for the FULL chunk asynchronously. This returns immediately.
            CompletableFuture<OptionalChunk<Chunk>> fut =
                cm.getChunkFutureSyncOnMainThread(req.pos.x, req.pos.z, ChunkStatus.FULL, true);

            // Track in-flight by long key
            IN_FLIGHT.put(req.pos.toLong(), req);

            fut.whenComplete((chunk, err) -> {
                // Hop back onto MAIN thread before touching world/game objects.
                server.execute(() -> {
                    try {
                        var active = IN_FLIGHT.remove(req.pos.toLong());
                        if (active == null || req.handle.isCancelled()) { unforce(req); return; }

                        if (err != null) {
                            if (req.onTimeout != null) req.onTimeout.run();
                            unforce(req);
                            return;
                        }

                        var loadedChunk = chunk.orElse(null);
                        if (loadedChunk != null) {
                            req.onReady.accept(loadedChunk);
                        } else {
                            if (req.onTimeout != null) req.onTimeout.run();
                        }
                    } finally {
                        unforce(req);
                        // After finishing one, try to launch more same tick.
                        pump();
                    }
                });
            });
        }
    }

    private static void unforce(Request req) {
        req.world.setChunkForced(req.pos.x, req.pos.z, false);
    }
}
