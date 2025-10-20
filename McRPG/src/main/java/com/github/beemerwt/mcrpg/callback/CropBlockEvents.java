package com.github.beemerwt.mcrpg.callback;

import com.github.beemerwt.mcrpg.proxies.CropBlockProxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

public class CropBlockEvents {

    public interface RandomTick {
        void onRandomTick(ServerWorld world, CropBlockProxy proxy, Random random);
    }

    public static final Event<CropBlockEvents.RandomTick> RANDOM_TICK = EventFactory.createArrayBacked(
            CropBlockEvents.RandomTick.class,
            (listeners) -> (world, proxy, random) -> {
                for (CropBlockEvents.RandomTick event : listeners) {
                    event.onRandomTick(world, proxy, random);
                }
            }
    );
}
