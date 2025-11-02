package com.github.beemerwt.essence.client;

import com.github.beemerwt.essence.core.Essence;
import net.fabricmc.api.ClientModInitializer;

/**
 * If a player has the Essence mod on the client they have access to additional features.
 */
public class EssenceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Essence.getLogger().info("Essence client mod initialized");
        Noclip.register();

        Hotkeys.register();
    }
}
