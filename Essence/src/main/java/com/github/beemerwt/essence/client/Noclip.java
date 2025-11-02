package com.github.beemerwt.essence.client;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.duck.INoclip;
import com.github.beemerwt.essence.core.payload.NoclipCapC2SPayload;
import com.github.beemerwt.essence.core.payload.NoclipSyncS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;

public class Noclip {
    private static final Hotkeys.Hotkey TOGGLE_NOCLIP =
        Hotkeys.registerHotkey(Hotkeys.ESSENCE_GENERAL, "key.essence.noclip_toggle", GLFW.GLFW_KEY_V);

    public static void register() {
        // Tell the server we can handle smooth noclip packets
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            ClientPlayNetworking.send(new NoclipCapC2SPayload())
        );

        // Receive noclip on/off
        ClientPlayNetworking.registerGlobalReceiver(NoclipSyncS2CPayload.ID,
            (payload, context) -> {
                boolean on = payload.on();
                context.client().execute(() -> {
                    var p = context.client().player;
                    if (p != null) {
                        Essence.getLogger().info("Noclip {} by server", on ? "enabled" : "disabled");
                        ((INoclip)p).essence$setNoclip(on);
                    }
                });
            });

        TOGGLE_NOCLIP.onKeyPressedThisTick(client -> {
            if (client.currentScreen == null && client.player != null && client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatCommand("noclip");
            }
        });
    }
}
