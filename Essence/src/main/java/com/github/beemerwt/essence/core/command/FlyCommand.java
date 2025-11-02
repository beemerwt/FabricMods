package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class FlyCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "fly").requires(Perms.FLY)
            .then(argument("player", EntityArgumentType.player())
                .requires(Perms.FLY.child("other"))
                .executes(ctx -> exec(ctx,
                    EntityArgumentType.getPlayer(ctx, "player")))
            )

            // default to self
            .executes(ctx -> exec(ctx, ctx.getSource().getPlayer()))
        );
    }

    private static int exec(CommandContext<ServerCommandSource> ctx, @Nullable ServerPlayerEntity target) {
        if (target == null) {
            ctx.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }

        boolean enable = !target.getAbilities().allowFlying;
        var name = target.getStringifiedName();

        // Respect creative/spectator when disabling
        if (!enable && (target.isCreative() || target.isSpectator())) {
            ctx.getSource().sendFeedback(() -> Text.literal(name + " is already in a mode that allows flight."), false);
            return 0;
        }

        var ab = target.getAbilities();
        ab.allowFlying = enable;
        if (!enable) ab.flying = false;

        target.sendAbilitiesUpdate();

        // Self notification
        if (target.equals(ctx.getSource().getPlayer())) {
            ctx.getSource().sendFeedback(() -> Text.literal("Flight " + (enable ? "enabled" : "disabled") + "."), false);
            return 1;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("Flight " + (enable ? "enabled" : "disabled")
            + " for " + name + "."), false);
        return 1;
    }
}
