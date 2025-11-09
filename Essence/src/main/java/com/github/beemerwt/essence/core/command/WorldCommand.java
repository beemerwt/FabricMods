package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.command.argument.RegistryKeyArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.Set;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.server.command.CommandManager.argument;

public class WorldCommand {

    private static final DynamicCommandExceptionType worldNotFoundException =
        new DynamicCommandExceptionType(worldKey -> Text.literal("World not found: " + worldKey.toString()));

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "world").requires(Perms.WORLD)
            .then(argument("world", RegistryKeyArgumentType.registryKey(RegistryKeys.WORLD))
                .executes(WorldCommand::exec)
            )
        );
    }

    private static int exec(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrThrow();
        var server = Essence.getServer();

        var targetWorldKey = RegistryKeyArgumentType.getKey(ctx, "world", RegistryKeys.WORLD, worldNotFoundException);
        ServerWorld toWorld = server.getWorld(targetWorldKey);
        if (toWorld == null) {
            ctx.getSource().sendError(Text.literal("World not found: " + targetWorldKey.getValue()));
            return 0;
        }

        ServerWorld fromWorld = player.getEntityWorld();
        final double fromScale = fromWorld.getDimension().coordinateScale();
        final double toScale = toWorld.getDimension().coordinateScale();
        final double scale = fromScale / toScale;

        var pos = player.getEntityPos();
        double x = pos.x * scale;
        double z = pos.z * scale;

        final WorldBorder border = toWorld.getWorldBorder();
        final double minX = border.getBoundWest()  + 1.0;
        final double maxX = border.getBoundEast()  - 1.0;
        final double minZ = border.getBoundNorth() + 1.0;
        final double maxZ = border.getBoundSouth() - 1.0;

        x = Math.clamp(x, minX, maxX);
        z = Math.clamp(z, minZ, maxZ);

        // Clamp Y to build height of target dim
        int y = toWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));

        player.teleport(toWorld, x, y, z, Set.of(), player.getYaw(), player.getPitch(), false);

        ctx.getSource().sendFeedback(() -> Text.literal("Teleported to " + toWorld.getRegistryKey().getValue()), false);
        return 1;
    }
}
