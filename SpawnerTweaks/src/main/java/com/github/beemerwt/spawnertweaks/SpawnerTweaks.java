package com.github.beemerwt.spawnertweaks;

import com.github.beemerwt.spawnertweaks.command.SpawnerTweaksCommands;
import com.github.beemerwt.spawnertweaks.config.ConfigManager;
import com.github.beemerwt.spawnertweaks.runtime.SpawnerCapTracker;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpawnerTweaks implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SpawnerTweaks");

    @Override
    public void onInitializeServer() {
        ConfigManager.load();
        SpawnerTweaksCommands.register();
        SpawnerCapTracker.register();
    }
}

