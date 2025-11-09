package com.github.beemerwt.mcrpg.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.List;

public final class TickClock {

    private static class ScheduledTask {
        long executeAt;
        Runnable task;
        ScheduledTask(long executeAt, Runnable task) {
            this.executeAt = executeAt;
            this.task = task;
        }
    }

    private static volatile long tick; // main thread writes
    private static final List<ScheduledTask> scheduledTasks = new ArrayList<>();
    public static long now() { return tick; }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;

            for (int i = 0; i < scheduledTasks.size(); ) {
                var scheduledTask = scheduledTasks.get(i);
                if (scheduledTask.executeAt <= tick) {
                    scheduledTask.task.run();
                    scheduledTasks.remove(i);
                } else {
                    i++;
                }
            }
        });
    }

    public static void schedule(long delayTicks, Runnable task) {
        long executeAt = now() + delayTicks;
        scheduledTasks.add(new ScheduledTask(executeAt, task));
    }
}
