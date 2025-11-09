package com.github.beemerwt.shulkerplace.core;

import com.github.beemerwt.shulkerplace.net.PickFromShulkerPayload;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ShulkerPlace implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PickFromShulkerPayload.ID, PickFromShulkerPayload.CODEC);

        LogUtils.getLogger().info("ShulkerPlaceServer initializing - registering pick from shulker receiver");
        ServerPlayNetworking.registerGlobalReceiver(
            PickFromShulkerPayload.ID,
            (payload, context) -> {
                // You are already on Netty thread; hop to server thread
                context.server().execute(() -> PickLogic.handlePickLogic(context.player(), payload));
            }
        );
    }
}
