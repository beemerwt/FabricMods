package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.command.argument.EntityArgumentType.player;

public final class TpaCommands {
    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();
    private static final long TIMEOUT_SECONDS = 60;

    private record Request(UUID sender, long createdAt) {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "tpa").requires(Perms.TPA)
            .then(argument("target", player())
                .executes(ctx ->
                    sendRequest(ctx.getSource().getPlayer(), getPlayer(ctx, "target"))
                )
            )
        );

        d.register(commandLiteral("essence", "tpaccept").requires(Perms.TPA)
            .executes(ctx ->
                acceptRequest(ctx.getSource().getPlayer())
            )
        );

        d.register(commandLiteral("essence", "tpdeny").requires(Perms.TPA)
            .executes(ctx ->
                denyRequest(ctx.getSource().getPlayer())
            )
        );
    }

    private static int sendRequest(@Nullable ServerPlayerEntity sender, @Nullable ServerPlayerEntity target) {
        if (sender == null) {
            Essence.getLogger().warn("Non-player tried to send TPA request.");
            return 0;
        }

        if (target == null) {
            sender.sendMessage(Text.literal("Target player not found.").formatted(Formatting.RED));
            return 0;
        }

        if (sender.equals(target)) {
            sender.sendMessage(Text.literal("You cannot send a TPA to yourself.").formatted(Formatting.RED));
            return 0;
        }

        UUID targetId = target.getUuid();
        Request existing = REQUESTS.get(targetId);

        if (existing != null && !isExpired(existing)) {
            sender.sendMessage(Text.literal("That player already has a pending request.").formatted(Formatting.RED));
            return 0;
        }

        REQUESTS.put(targetId, new Request(sender.getUuid(), System.currentTimeMillis()));
        sender.sendMessage(Text.literal("Teleport request sent to ").formatted(Formatting.GREEN)
                .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW)));

        target.sendMessage(Text.literal(sender.getName().getString()).formatted(Formatting.YELLOW)
                .append(Text.literal(" wants to teleport to you.").formatted(Formatting.GREEN)));

        target.sendMessage(Text.literal("Type ").formatted(Formatting.GREEN)
                .append(Text.literal("/tpaccept").formatted(Formatting.AQUA))
                .append(Text.literal(" to allow or ").formatted(Formatting.GREEN))
                .append(Text.literal("/tpdeny").formatted(Formatting.RED))
                .append(Text.literal(" to refuse.").formatted(Formatting.GREEN)));

        Essence.getLogger().info("{} sent teleport request to {}", sender.getName().getString(),
                target.getName().getString());
        return 1;
    }

    private static int acceptRequest(@Nullable ServerPlayerEntity target) {
        if (target == null) {
            Essence.getLogger().warn("Non-player tried to accept TPA request.");
            return 0;
        }

        Request request = REQUESTS.remove(target.getUuid());
        if (request == null || isExpired(request)) {
            target.sendMessage(Text.literal("You have no pending teleport requests.").formatted(Formatting.RED));
            return 0;
        }

        var server = Essence.getServer();
        ServerPlayerEntity sender = server.getPlayerManager().getPlayer(request.sender());
        if (sender == null) {
            target.sendMessage(Text.literal("That player is no longer online.").formatted(Formatting.RED));
            return 0;
        }

        var posFlags = PositionFlag.combine(PositionFlag.ofDeltaPos(false, false, false),
                PositionFlag.ofRot(false, false));
        sender.teleport(target.getEntityWorld(), target.getX(), target.getY(), target.getZ(),
                posFlags, sender.getYaw(), sender.getPitch(), false);

        sender.sendMessage(Text.literal("Teleported to ").formatted(Formatting.GREEN)
                .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW)));

        target.sendMessage(Text.literal("Accepted teleport request from ").formatted(Formatting.GREEN)
                .append(Text.literal(sender.getName().getString()).formatted(Formatting.YELLOW)));
        return 1;
    }

    private static int denyRequest(@Nullable ServerPlayerEntity target) {
        if (target == null) {
            Essence.getLogger().warn("Non-player tried to deny TPA request.");
            return 0;
        }

        Request request = REQUESTS.remove(target.getUuid());
        if (request == null || isExpired(request)) {
            target.sendMessage(Text.literal("You have no pending teleport requests.").formatted(Formatting.RED));
            return 0;
        }

        var server = Essence.getServer();
        ServerPlayerEntity sender = server.getPlayerManager().getPlayer(request.sender());
        if (sender != null) {
            sender.sendMessage(Text.literal("Your teleport request to ").formatted(Formatting.RED)
                    .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW))
                    .append(Text.literal(" was denied.").formatted(Formatting.RED)));
        }

        target.sendMessage(Text.literal("Teleport request denied.").formatted(Formatting.YELLOW));
        return 1;
    }

    private static boolean isExpired(Request req) {
        return (System.currentTimeMillis() - req.createdAt()) > TIMEOUT_SECONDS * 1000;
    }

    private static boolean hasRequest(ServerPlayerEntity player) {
        Request req = REQUESTS.get(player.getUuid());
        if (req == null) return false;

        if (isExpired(req)) {
            REQUESTS.remove(player.getUuid());
            return false;
        }

        return true;
    }
}
