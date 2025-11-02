package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.data.model.StoredLocation;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.Locations;
import com.github.beemerwt.essence.core.util.Teleporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class DirectionCommands {
    private static final int WORLD_CEILING = 319;
    private static final int WORLD_FLOOR = -69;

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "top").requires(Perms.TOP)
            .then(argument("player", EntityArgumentType.player())
                .requires(Perms.TOP.child("other"))
                .executes(ctx -> exec(ctx,
                    EntityArgumentType.getPlayer(ctx, "player"), Direction.UP, false)))

            // Default to executing on self if no player argument given
            .executes(ctx -> exec(ctx,
                ctx.getSource().getPlayer(), Direction.UP, false))
        );

        d.register(commandLiteral("essence", "bottom").requires(Perms.BOTTOM)
            .then(argument("player", EntityArgumentType.player())
                .requires(Perms.BOTTOM.child("other"))
                .executes(ctx -> exec(ctx,
                    EntityArgumentType.getPlayer(ctx, "player"), Direction.DOWN, false)))

            // Default to executing on self if no player argument given
            .executes(ctx -> exec(ctx,
                ctx.getSource().getPlayer(), Direction.DOWN, false))
        );

        d.register(commandLiteral("essence", "up").requires(Perms.UP)
            .then(argument("player", EntityArgumentType.player())
                .requires(Perms.UP.child("other"))
                .executes(ctx -> exec(ctx,
                    EntityArgumentType.getPlayer(ctx, "player"), Direction.UP, true)))

            // Default to executing on self if no player argument given
            .executes(ctx -> exec(ctx,
                ctx.getSource().getPlayer(), Direction.UP, true))
        );

        d.register(commandLiteral("essence", "down").requires(Perms.DOWN)
            .then(argument("player", EntityArgumentType.player())
                .requires(Perms.DOWN.child("other"))
                .executes(ctx -> exec(ctx,
                    EntityArgumentType.getPlayer(ctx, "player"), Direction.DOWN, true)))

            // Default to executing on self if no player argument given
            .executes(ctx -> exec(ctx,
                ctx.getSource().getPlayer(), Direction.DOWN, true))
        );
    }

    private static int exec(
        CommandContext<ServerCommandSource> ctx, @Nullable ServerPlayerEntity target, Direction dir, boolean first
    ) {
        if (target == null) {
            ctx.getSource().sendError(Text.literal("Invalid player."));
            return 0;
        }

        ServerWorld world = target.getEntityWorld();
        BlockPos start = target.getBlockPos();
        BlockPos safe = findSafePos(world, start, dir, first);

        if (start.equals(safe)) {
            ctx.getSource().sendError(Text.literal("No safe surface found nearby."));
            return 0;
        }

        StoredLocation loc = Locations.fromPlayer(target, safe);
        Teleporter.teleportSavingBack(target, loc);
        target.fallDistance = 0;

        var dirText = (dir == Direction.UP) ? "surface" : "depths";

        ctx.getSource().sendFeedback(() -> Text.literal("Woosh! You’ve been sent to the " + dirText + "."), false);
        return 1;
    }

    /**
     * Finds a safe position for a player to stand in the given column (x, z).
     * Direction.UP searches upward (used for /top),
     * Direction.DOWN searches downward (used for /bottom).
     * <p>
     * The returned position is where the player's feet should be.
     */
    private static BlockPos findSafePos(ServerWorld world, BlockPos start, Direction dir, boolean first) {
        BlockPos.Mutable pos = new BlockPos.Mutable(start.getX(), start.getY(), start.getZ());

        // start by assuming the player is on standable ground
        int furthestY = start.getY();

        // scan vertically depending on direction
        if (dir == Direction.UP) {
            for (int y = start.getY(); y < WORLD_CEILING; y++) {
                pos.setY(y);
                if (isStandableGround(world, pos.down()) && isAiry(world, pos) && isAiry(world, pos.up())) {
                    furthestY = y;
                    if (first) break;
                }
            }
        } else { // DOWN (bottom)
            for (int y = start.getY(); y > WORLD_FLOOR; y--) {
                pos.setY(y);
                if (isStandableGround(world, pos.down()) && isAiry(world, pos) && isAiry(world, pos.up())) {
                    furthestY = y;
                    if (first) break;
                }
            }
        }

        return new BlockPos(pos.getX(), furthestY, pos.getZ());
    }

    private static boolean isStandableGround(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.getCollisionShape(world, pos).isEmpty()) return false;

        // Disallow obvious hazards (lava, fire, campfires, cactus)
        if (!world.getFluidState(pos).isEmpty() && world.getFluidState(pos).isStill())
            return false;

        var block = state.getBlock();
        var id = Registries.BLOCK.getId(block).toString();
        // quick cheap checks without depending on tags:
        return !id.contains("lava") && !id.contains("fire") && !id.contains("campfire") && !id.contains("cactus");
    }

    private static boolean isAiry(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir() && world.getFluidState(pos).isEmpty();
    }
}
