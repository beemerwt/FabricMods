package com.github.beemerwt.mcrpg.util;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class CommandUtils {

    @FunctionalInterface
    public interface CommandBody { int run() throws Exception; }

    public static int safe(CommandContext<ServerCommandSource> ctx, CommandBody body) {
        try {
            return body.run();
        } catch (Exception e) {
            return fail(ctx, "Internal error: " + e.getMessage());
        }
    }

    public static int ok(CommandContext<ServerCommandSource> ctx, String msg, boolean broadcastToOps) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg), broadcastToOps);
        return Command.SINGLE_SUCCESS;
    }

    public static int fail(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendError(Text.literal(msg));
        return 0;
    }
}
