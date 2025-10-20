package com.github.beemerwt.essence.command;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class BackCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(
            literal("back")
                .requires(src -> Permissions.check(src, "essence.back", OpLevel.SUPER_MOD))
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
