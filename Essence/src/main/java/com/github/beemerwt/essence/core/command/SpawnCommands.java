package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.CommandOverrideUtil;
import com.github.beemerwt.essence.core.util.Locations;
import com.github.beemerwt.essence.core.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.WorldProperties;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class SpawnCommands {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "spawn");

        // /spawn always goes to the server's natural world spawn (Overworld)
        d.register(commandLiteral("essence", "spawn").requires(Perms.SPAWN)
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

        d.register(commandLiteral("essence", "setspawn").requires(Perms.SET_SPAWN)
            .executes(ctx -> execSetSpawn(ctx.getSource()))
        );
    }

    private static int execSetSpawn(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Spawn points can only be set in-game."));
            return 0;
        }

        var world = p.getEntityWorld();
        var pos = p.getBlockPos();
        var yaw = p.getYaw();
        var pitch = p.getPitch();

        try {
            var newSpawn = WorldProperties.SpawnPoint.create(world.getRegistryKey(), pos, yaw, pitch);
            world.setSpawnPoint(newSpawn);
            src.sendFeedback(() -> Text.literal("Spawn point set to ").append(
                Text.literal(pos.toShortString()).formatted(Formatting.YELLOW)), true);
        } catch (Exception e) {
            Essence.getLogger().warn(e, "Failed to set spawn point in " + world.getRegistryKey().getValue().toString()
                + " to " + pos.toShortString());
            src.sendError(Text.literal("An error occurred while setting the spawn point."));
            return 0;
        }

        return 1;
    }
}
