package com.github.beemerwt.essence.command;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.CommandOverrideUtil;
import com.github.beemerwt.essence.util.Locations;
import com.github.beemerwt.essence.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.WorldProperties;

public class SpawnCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "spawn");

        // /spawn always goes to the server's natural world spawn (Overworld)
        d.register(CommandManager.literal("spawn")
                .requires(src -> Permissions.check(src, "essence.spawn", OpLevel.NONE))
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) return 0;

                    var world = Essence.getServer().getSpawnWorld();
                    WorldProperties.SpawnPoint sp = world.getSpawnPoint();
                    if (sp == null) {
                        ctx.getSource().sendError(Text.literal("The spawn point is not set."));
                        return 0;
                    }

                    var loc = Locations.fromSpawnPoint(sp);
                    Teleporter.teleportSavingBack(p, loc);
                    ctx.getSource().sendFeedback(() -> Text.literal("Teleported to spawn."), false);
                    return 1;
                })
        );
    }
}
