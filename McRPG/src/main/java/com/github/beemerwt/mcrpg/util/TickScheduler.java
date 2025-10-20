package com.github.beemerwt.mcrpg.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class TickScheduler {

    private TickScheduler() {}

    /** Logical server tick counter since registration. */
    private static long tick = 0L;

    /** dueTick -> queue of tasks to run at that tick */
    private static final Map<Long, ArrayDeque<Task>> wheel = new HashMap<>();

    /** Simple lock so scheduling/canceling from other threads can’t race the server tick. */
    private static final Object LOCK = new Object();

    // --- Task type (compatible with your previous usage) ---------------------

    public static final class Task {
        private final long dueTick;
        private final Runnable action;

        private Task(long dueTick, Runnable action) {
            this.dueTick = dueTick;
            this.action = action;
        }

        /** Estimated remaining ticks until this task is due (0 if already due/past). */
        public long getRemainingTicks() {
            synchronized (LOCK) {
                long remain = dueTick - tick;
                return Math.max(0L, remain);
            }
        }

        /** Cancel this task if it hasn’t run yet. */
        public void cancel() {
            TickScheduler.cancel(this);
        }
    }

    // --- Hook into server tick ------------------------------------------------

    static {
        ServerTickEvents.END_SERVER_TICK.register(TickScheduler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        final ArrayDeque<Task> due;
        synchronized (LOCK) {
            tick++; // advance tick first
            due = wheel.remove(tick); // pop all tasks scheduled for this tick
        }

        if (due != null) {
            Task t;
            while ((t = due.pollFirst()) != null) {
                try {
                    t.action.run();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // --- Public API -----------------------------------------------------------

    /**
     * Schedule a Runnable to run after {@code ticks} ticks.
     * A delay of 0 means “next tick” (not the current tick).
     */
    public static Task schedule(long ticks, Runnable action) {
        if (action == null) return null;

        final long when;
        synchronized (LOCK) {
            long delay = Math.max(0L, ticks);
            // Minimum delay is 1 so tasks never run in the same tick they’re scheduled.
            when = tick + Math.max(1L, delay);
            wheel.computeIfAbsent(when, k -> new ArrayDeque<>())
                    .addLast(new Task(when, action));
            // Return the tail we just added (deque’s last element)
            return wheel.get(when).peekLast();
        }
    }

    /** Convenience alias similar to your prior usage. */
    public static Task runLater(Runnable action, int ticks) {
        return schedule(ticks, action);
    }

    /** Cancel a scheduled task if it hasn’t run yet. */
    public static void cancel(Task task) {
        if (task == null) return;
        synchronized (LOCK) {
            var q = wheel.get(task.dueTick);
            if (q != null) {
                q.remove(task);
                if (q.isEmpty()) wheel.remove(task.dueTick);
            }
        }
    }
}
