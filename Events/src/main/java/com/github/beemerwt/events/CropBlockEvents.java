package com.github.beemerwt.events;

import com.github.beemerwt.events.proxy.CropBlockProxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

public class CropBlockEvents {

    public interface RandomTick {
        /**
         * Called when a crop block receives a random tick.
         * @param world The server world.
         * @param proxy A proxy containing information about the crop block.
         * @param random A random number generator.
         */
        void onRandomTick(ServerWorld world, CropBlockProxy proxy, Random random);
    }

    public static final Event<RandomTick> RANDOM_TICK = EventFactory.createArrayBacked(
            RandomTick.class,
            (listeners) -> (world, proxy, random) -> {
                for (RandomTick event : listeners) {
                    event.onRandomTick(world, proxy, random);
                }
            }
    );
}
