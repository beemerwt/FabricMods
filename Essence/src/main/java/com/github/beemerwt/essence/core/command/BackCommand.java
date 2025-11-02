package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.LocationType;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class BackCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "back").requires(Perms.BACK)
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) return 0;

                    var back = Essence.getLocationStore().getSingle(p.getUuid(), LocationType.BACK);
                    if (back.isEmpty()) {
                        ctx.getSource().sendError(Text.literal("No previous location."));
                        return 0;
                    }

                    Teleporter.teleportSavingBack(p, back.get());
                    ctx.getSource().sendFeedback(() -> Text.literal("Teleported back."), false);
                    return 1;
                })
        );
    }
}
