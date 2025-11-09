package com.github.beemerwt.mcrpg.util;

import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

// Utility class for sending messages to players through various components
public final class Messenger {
    private Messenger() {}

    public static void actionBar(ServerPlayerEntity player, Text text) {
        player.networkHandler.sendPacket(new OverlayMessageS2CPacket(text));
    }
}
