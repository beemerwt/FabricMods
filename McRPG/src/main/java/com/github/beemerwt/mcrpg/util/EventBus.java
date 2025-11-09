package com.github.beemerwt.mcrpg.util;

import com.github.beemerwt.mcrpg.event.GameEvent;
import net.minecraft.util.ActionResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Dispatcher: one-time Fabric wiring
public final class EventBus {
    @FunctionalInterface
    public interface ActionResultingListener<E extends GameEvent> {
        ActionResult on(E event);
    }

    @FunctionalInterface
    public interface Listener<E extends GameEvent> {
        void on(E event);
    }

    // store as raw-er to simplify variance at call sites
    private static final ConcurrentMap<Class<? extends GameEvent>, CopyOnWriteArrayList<Listener<?>>>
        listeners = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<? extends GameEvent>, CopyOnWriteArrayList<ActionResultingListener<?>>>
        arListeners = new ConcurrentHashMap<>();

    // note the <? super E> so a Listener<GameEvent> can subscribe to specific subtypes too
    public static <E extends GameEvent> void subscribe(Class<E> type, Listener<E> l) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(l);
    }

    public static <E extends GameEvent> void intercept(Class<E> type, ActionResultingListener<E> l) {
        arListeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(l);
    }

    @SuppressWarnings("unchecked")
    public static <E extends GameEvent> ActionResult emit(E event) {
        var list = listeners.get(event.getClass());
        var arList = arListeners.get(event.getClass());

        if (list != null)
            for (Listener<?> raw : list)
                ((Listener<E>)raw).on(event);

        if (arList != null) {
            for (ActionResultingListener<?> raw : arList) {
                ActionResult result = ((ActionResultingListener<E>)raw).on(event);
                if (result != ActionResult.PASS) {
                    return result;
                }
            }
        }

        return ActionResult.PASS;
    }
}
