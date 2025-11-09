package com.github.beemerwt.mcrpg.async;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.PlayerSnapshot;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.data.SaveCompletion;

import java.io.Closeable;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Writer: coalesces by player and never pushes work to caller. */
public final class AsyncSaver implements Runnable, Closeable {
    private final Connection c;
    private final Thread thread;

    /** latest snapshot per player (coalescing) */
    private final ConcurrentHashMap<UUID, PlayerSnapshot> latest = new ConcurrentHashMap<>();
    /** whether a UUID is already queued (prevents duplicates) */
    private final ConcurrentHashMap<UUID, Boolean> inQueue = new ConcurrentHashMap<>();
    /** queue of UUIDs to process; bounded; we never block on offer */
    private final ArrayBlockingQueue<UUID> queue;

    // Completion notifications from IO thread -> main thread
    public final ConcurrentLinkedQueue<SaveCompletion> completions = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(true);

    public AsyncSaver(Connection writeConn, int capacity) {
        this.c = writeConn;
        this.queue = new ArrayBlockingQueue<>(Math.max(256, capacity));
        this.thread = new Thread(this, "McRPG-PlayerStore-Writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Non-blocking: record the latest snapshot, enqueue UUID once if not already queued. */
    public void offer(PlayerSnapshot snap) {
        var id = snap.data().id;
        latest.put(id, snap);
        // try to mark as enqueued; if previously present, it's already in the queue
        if (inQueue.putIfAbsent(id, Boolean.TRUE) == null) {
            // first time for this cycle: try to enqueue UUID without blocking
            if (!queue.offer(id)) {
                // queue is full: give up quickly (never block). Will retry on next save attempt.
                inQueue.remove(id);
                // keep pd.dirty true so a future call will re-offer
            }
        }
    }

    @Override public void run() {
        while (running.get() || !queue.isEmpty()) {
            try {
                UUID id = queue.poll(250, TimeUnit.MILLISECONDS);
                if (id == null) continue;

                // capture the latest snapshot at dequeue time
                PlayerSnapshot snap = latest.get(id);
                boolean ok = PlayerStore.saveSnapshot(c, snap);
                completions.offer(new SaveCompletion(id, snap.seq(), ok));

                // mark dequeued; allow re-enqueue if a newer snapshot arrived during write
                inQueue.remove(id);

                // If a newer snapshot arrived while we were writing, latest still has it;
                // re-enqueue (non-blocking) to flush that newer state.
                if (latest.containsKey(id) && snap != latest.get(id)) {
                    // try enqueue again, but still never block
                    inQueue.putIfAbsent(id, Boolean.TRUE);
                    queue.offer(id); // if this fails due to saturation, it will be retried by a later offer()
                } else {
                    // we have flushed the latest; remove it to keep the map small
                    latest.remove(id, snap);
                }
            } catch (InterruptedException ie) {
                // graceful stop
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                McRPG.getLogger().error(t, "AsyncSaver loop error");
            }
        }
    }

    public void shutdownAndJoin(Duration maxWait) {
        running.set(false);
        thread.interrupt();
        try {
            thread.join(maxWait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void close() {
        running.set(false);
        thread.interrupt();
    }
}
