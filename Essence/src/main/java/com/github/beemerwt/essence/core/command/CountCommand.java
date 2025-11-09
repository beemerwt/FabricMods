package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.server.command.CommandManager.argument;

public class CountCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "count").requires(Perms.COUNT)
            .then(argument("entities", EntityArgumentType.entities())
            .executes(CountCommand::exec))
        );
    }

    private static int exec(CommandContext<ServerCommandSource> ctx) {
        try {
            var entities = EntityArgumentType.getEntities(ctx, "entities");
            var count = entities.size();
            ctx.getSource().sendFeedback(() -> Text.literal("Counted " + count + " entities."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("An error occurred while counting entities."));
            return 0;
        }
    }
}
