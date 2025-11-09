package com.github.beemerwt.mcrpg.data;

import com.github.beemerwt.mcrpg.McRPG;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class SuperAbilityRuntime {
    private static final long MAX_READY_TICKS = 60L; // 3 seconds

    public enum State { IDLE, READY, ACTIVE, COOLDOWN }
    public enum Cause { PLAYER_REQUEST, AUTO_EXPIRE, COMPLETE }

    public static final class Transition {
        public final UUID playerId;
        public final State from, to;
        public final Cause cause;

        public Transition(UUID playerId, State from, State to, Cause cause) {
            this.playerId = playerId;
            this.from = from;
            this.to = to;
            this.cause = cause;
        }
    }

    private final static class Entry {
        State state = State.IDLE;
        long readyUntil = 0L;
        long activeUntil = 0L;
        long cooldownUntil = 0L;
    }

    private final Map<UUID, Entry> states = new ConcurrentHashMap<>();
    private final ArrayDeque<Transition> transitions = new ArrayDeque<>();
    private final LongSupplier tick; // e.g., TickClock::now

    public SuperAbilityRuntime(LongSupplier tickClock) {
        this.tick = tickClock;
    }

    public State getState(ServerPlayerEntity player) {
        var e = states.get(player.getUuid());
        return (e == null) ? State.IDLE : e.state;
    }

    public void tryReady(ServerPlayerEntity player)
    {
        var e = states.computeIfAbsent(player.getUuid(), k -> new Entry());
        var prev = e.state;
        if (prev == State.ACTIVE || prev == State.COOLDOWN) return;
        e.state = State.READY;
        e.readyUntil = tick.getAsLong() + MAX_READY_TICKS;
        transitions.add(new Transition(player.getUuid(), prev, State.READY, Cause.PLAYER_REQUEST));
    }

    public boolean tryActivate(ServerPlayerEntity player, long durationTicks, long cooldownTicks) {
        var entry = states.computeIfAbsent(player.getUuid(), k -> new Entry());
        if (entry.state != State.READY)
            return false;

        entry.state = State.ACTIVE;
        entry.activeUntil = tick.getAsLong() + Math.max(1, durationTicks);
        entry.cooldownUntil = entry.activeUntil + Math.max(0, cooldownTicks);
        transitions.add(new Transition(player.getUuid(), State.READY, State.ACTIVE, Cause.PLAYER_REQUEST));
        return true;
    }

    /** Advance timers; call once per tick by the owning SuperAbility. */
    public void tick() {
        transitions.clear();
        long now = tick.getAsLong();

        for (var it = states.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var id = entry.getKey();
            var e = entry.getValue();

            if (e.state == State.READY && now >= e.readyUntil) {
                e.state = State.IDLE;
                transitions.add(new Transition(id, State.READY, State.IDLE, Cause.AUTO_EXPIRE));
                it.remove(); // back to idle => drop entry
                continue;
            }
            if (e.state == State.ACTIVE && now >= e.activeUntil) {
                e.state = State.COOLDOWN;
                transitions.add(new Transition(id, State.ACTIVE, State.COOLDOWN, Cause.COMPLETE));
                continue;
            }
            if (e.state == State.COOLDOWN && now >= e.cooldownUntil) {
                transitions.add(new Transition(id, State.COOLDOWN, State.IDLE, Cause.COMPLETE));
                it.remove();
            }
        }
    }

    /** Get transitions produced by the last tick(). */
    public List<Transition> drainTransitions() {
        var out = new ArrayList<>(transitions);
        transitions.clear();
        return out;
    }

    public void clearPlayerData(UUID playerId) {
        states.remove(playerId);
    }
}
