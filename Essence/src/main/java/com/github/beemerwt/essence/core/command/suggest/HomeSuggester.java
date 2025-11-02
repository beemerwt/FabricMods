package com.github.beemerwt.essence.core.command.suggest;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.LocationType;
import com.github.beemerwt.essence.core.data.model.PlayerData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;

public class HomeSuggester {

    public static final SuggestionProvider<ServerCommandSource> SELF =
        (ctx, b) -> {
            if (ctx.getSource().getPlayer() == null) return b.buildFuture();
            var sp = ctx.getSource().getPlayer();

            var homes = Essence.getLocationStore().list(sp.getUuid(), LocationType.HOME);
            homes.keySet().forEach(b::suggest);
            return b.buildFuture();
        };

    public static final SuggestionProvider<ServerCommandSource> OTHER =
        (ctx, b) -> {
            PlayerData data = PlayerSuggester.getPlayer(ctx, "player").orElse(null);
            if (data == null) {
                throw new CommandSyntaxException(
                    CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException(),
                    () -> "Player not found");
            }

            Essence.getLocationStore().list(data.uuid(), LocationType.HOME).keySet().forEach(b::suggest);
            b.suggest("bed"); // explicitly include bed and handle it as SPAWN point
            return b.buildFuture();
        };
}
