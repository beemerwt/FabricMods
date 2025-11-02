package com.github.beemerwt.essence.core.util;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public class Messenger {
    public static void sendBanNotification(MinecraftServer server, UUID target, @Nullable Instant expires, String reason) {
        var pm = server.getPlayerManager();
        var shouldBroadcast = Essence.getConfig().announceSuspensions;

        Text message = Text.literal("Player ")
            .append(Text.literal(nameOf(server, target)).formatted(Formatting.YELLOW))
            .append(Text.literal(" has been banned "))
            .append(getExpiryText(expires))
            .append(reason != null ? Text.literal(": " + reason) : Text.empty());

        for (var player : pm.getPlayerList()) {
            if (!shouldBroadcast && !Perms.BAN.check(player))
                continue;

            player.sendMessage(message, false);
        }
    }

    private static Text getExpiryText(@Nullable Instant expires) {
        if (expires == null) {
            return Text.literal("permanently");
        } else {
            var expiryString = TimeFormats.formatDurationFromNow(expires);
            return Text.literal("for ").append(Text.literal(expiryString).formatted(Formatting.YELLOW));
        }
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        var player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return player.getStringifiedName();
        } else {
            return Essence.getPlayerStore().get(uuid).name();
        }
    }
}
