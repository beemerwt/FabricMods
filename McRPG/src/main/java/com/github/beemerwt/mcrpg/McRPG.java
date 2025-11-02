package com.github.beemerwt.mcrpg;

import com.github.beemerwt.mcrpg.command.AdminCommand;
import com.github.beemerwt.mcrpg.command.SkillCommand;
import com.github.beemerwt.mcrpg.events.*;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.data.PlayerStore;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.skills.*;
import com.github.beemerwt.mcrpg.ui.HealthbarHover;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import com.github.beemerwt.mcrpg.util.FabricLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

// TODO: Shift+Right-Click on copper blocks with an axe needs to fallthrough
// TODO: Survival only
// TODO: Herbalism should reward xp for all the blocks that break when it's a column (e.g. bamboo, sugarcane, cactus)
// TODO: Herbalism fix for blocks like shroomlight -- stop awarding xp when breaking player-placed
// TODO: Announcements in server when player reaches milestone

public class McRPG implements ModInitializer {
    private static final FabricLogger LOG = FabricLogger.getLogger("McRPG");

    public static FabricLogger getLogger() {
        return LOG;
    }

    private static MinecraftServer server;
    private static PlayerStore store;

    private long lastSave = 0;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            LOG.debug("Server started {}", s.getVersion());
            server = s;
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

        LOG.info("Initializing");
        ConfigManager.init();            // loads defaults + overrides
        AbilityManager.init();

        store = PlayerStore.create();
        PlacedBlockTracker.register();

        AbilityEvents.register();
        CombatEvents.register();

        CommandRegistrationCallback.EVENT.register((d, access, regEnv) -> {
            SkillCommand.register(d);
            AdminCommand.register(d);
        });

        XpBossbarManager.init();
        HealthbarHover.init();

        Acrobatics.register();
        Excavation.register();
        Herbalism.register();
        Mining.register();
        Repair.register();
        Salvage.register();
        Smelting.register();
        Swords.register();
        Woodcutting.register();

        ServerPlayerEvents.JOIN.register(player -> {
            store.ensurePlayerRow(player.getUuid(), player.getStringifiedName());
            store.get(player); // touch the player to load or create their data
        });

        lastSave = System.currentTimeMillis();
        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
            long now = System.currentTimeMillis();
            if (now - lastSave > ConfigManager.getGeneralConfig().autoSaveEverySeconds * 1000L) {
                try {
                    LOG.info("Auto-saving players...");
                    store.saveAll();
                } catch (Exception e) {
                    LOG.error(e, "Error during autosave");
                }
                lastSave = now;
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            try {
                LOG.info("Shutting down; saving players...");
                store.saveAll();
                if (store instanceof AutoCloseable ac) ac.close();
            } catch (Exception e) {
                LOG.error(e, "Error during shutdown save");
            }
        });

        LOG.info("Initialized");
    }

    public static PlayerStore getStore() {
        return store;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
