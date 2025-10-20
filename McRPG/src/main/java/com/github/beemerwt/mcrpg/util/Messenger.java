package com.github.beemerwt.mcrpg.util;

import com.github.beemerwt.mcrpg.text.Component;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

// Utility class for sending messages to players through various components
public final class Messenger {
    private Messenger() {}

    public static void actionBar(ServerPlayerEntity player, Component component) {
        player.networkHandler.sendPacket(new OverlayMessageS2CPacket(component.toText()));
    }
}
