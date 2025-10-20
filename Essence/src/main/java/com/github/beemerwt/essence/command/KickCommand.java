package com.github.beemerwt.essence.command;

import com.github.beemerwt.essence.command.suggest.PlayerSuggester;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.CommandOverrideUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KickCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "kick");

        d.register(
            literal("kick")
                .then(argument("player", StringArgumentType.word()).suggests(PlayerSuggester.CONNECTED)
                    .requires(src -> Permissions.check(src, "essence.kick", OpLevel.MODERATOR))
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        var target = PlayerSuggester.getPlayer(ctx, "player");
                        return target.map(playerData -> {
                            var player = src.getServer().getPlayerManager().getPlayer(playerData.uuid());
                            if (player != null) {
                                player.networkHandler.disconnect(Text.literal("Kicked from server"));
                                src.sendFeedback(() ->
                                        Text.literal(src.getName() + " kicked " + player.getName().getString())
                                                .formatted(Formatting.YELLOW), true);
                                return 1;
                            } else {
                                src.sendError(Text.literal("Player is not online").formatted(Formatting.RED));
                                return 0;
                            }
                        }).orElse(0);
                    })
                )
        );
    }
}
