package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.async.AsyncChunkBroker;
import com.github.beemerwt.essence.core.inventory.PermissiveInventory;
import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.time.Duration;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class InvSeeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "invsee").requires(Perms.INV_SEE.orChildren("containers", "players"))
                // Containers by position
                .then(argument("pos", BlockPosArgumentType.blockPos())
                    .requires(Perms.INV_SEE.orChild("containers"))
                    .executes(ctx -> execContainer(ctx,
                        BlockPosArgumentType.getBlockPos(ctx, "pos")))
                )
                // Player inventories
                .then(argument("player", EntityArgumentType.player())
                    .requires(Perms.INV_SEE.orChild("players"))
                    .executes(ctx -> execOther(ctx,
                        EntityArgumentType.getPlayer(ctx, "player")))
                )
        );
    }

    /** Open a container at a block position (double-chest aware). */
    public static int execContainer(CommandContext<ServerCommandSource> ctx, BlockPos targetPos) {
        final var source = ctx.getSource();
        final var viewer = source.getPlayer();
        if (viewer == null) {
            ctx.getSource().sendError(Text.literal("This command can only be used in-game."));
            return 0;
        }

        AsyncChunkBroker.enqueue(
            viewer.getUuid(),
            viewer.getEntityWorld(),
            new ChunkPos(viewer.getBlockPos()),
            Duration.ofSeconds(8),
            chunk -> handleContainer(viewer, targetPos),
            () -> source.sendError(Text.literal("Timed out waiting to open the inventory."))
        );

        return 1;
    }

    private static void handleContainer(ServerPlayerEntity viewer, BlockPos targetPos) {
        final var world = viewer.getEntityWorld();
        final var state = world.getBlockState(targetPos);
        if (state.isAir()) {
            viewer.sendMessage(Text.literal("There is no block at that position.").formatted(Formatting.RED));
            return;
        }

        try {
            Inventory inv = null;

            if (world.getBlockEntity(targetPos) instanceof Inventory beInv)
                inv = beInv;

            if (state.getBlock() instanceof ChestBlock cb)
                inv = ChestBlock.getInventory(cb, state, world, targetPos, true);

            if (inv == null) {
                viewer.sendMessage(Text.literal("No inventory found at the specified location.")
                    .formatted(Formatting.RED));
                return;
            }

            Inventory wrapped = new PermissiveInventory(inv);
            final int size = wrapped.size();

            // 2) Prefer block/state factory; it merges double chests and respects locks/loot
            NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) -> {
                if (size == HopperBlockEntity.INVENTORY_SIZE)
                    return new HopperScreenHandler(syncId, playerInventory, wrapped);
                else if (size % 9 == 0) {
                    int rows = Math.clamp(size / 9, 1, 6);
                    return new GenericContainerScreenHandler(
                        switch (rows) {
                            case 1 -> ScreenHandlerType.GENERIC_9X1;
                            case 2 -> ScreenHandlerType.GENERIC_9X2;
                            case 3 -> ScreenHandlerType.GENERIC_9X3;
                            case 4 -> ScreenHandlerType.GENERIC_9X4;
                            case 5 -> ScreenHandlerType.GENERIC_9X5;
                            default -> ScreenHandlerType.GENERIC_9X6;
                        },
                        syncId, playerInventory, wrapped, rows
                    ) {
                        @Override
                        public boolean canUse(PlayerEntity p) {
                            return true;
                        } // extra safety
                    };
                } else {
                    // Fallback for odd sizes (rare)
                    Essence.getLogger().warn("InvSee opened container {} with non-multiple-of-9 size: {} at {} in {}",
                        state.getBlock().getName().getString(),
                        size, targetPos, world.getRegistryKey().getValue());
                    return new GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X3, // UI shape
                        syncId, playerInventory, wrapped, 3
                    ) {
                        @Override
                        public boolean canUse(PlayerEntity p) {
                            return true;
                        }
                    };
                }
            }, Text.literal("Container: " + state.getBlock().getName().getString()));

            // 4) Open it. Any exception here will be caught/logged below.
            viewer.openHandledScreen(factory);
        } catch (Throwable t) {
            // Make absolutely sure we see the error if something escapes
            viewer.sendMessage(Text.literal("Failed to open inventory (see server log).").formatted(Formatting.RED));
            Essence.getLogger().error(t, "Error opening /invsee inventory at {} in {}",
                targetPos, world.getRegistryKey().getValue());
        }
    }

    /** Open a 9x5 view bound to the target player's inventory (main+hotbar+armor+offhand). */
    public static int execOther(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity targetPlayer) {
        var viewer = ctx.getSource().getPlayer();
        if (viewer == null) {
            ctx.getSource().sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        if (viewer == targetPlayer && !Perms.INV_SEE.child("self").check(viewer)) {
            ctx.getSource().sendError(Text.literal("You cannot use /invsee on yourself."));
            return 0;
        }

        // Backing inventory that delegates to the target's slots.
        // var backing = new InvSeeInventory(targetPlayer);

        // 9x5 = 45 slots (36 main+hotbar + 4 armor + 1 offhand + 4 unused dummies)

        try {
            NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) ->
                    GenericContainerScreenHandler.createGeneric9x5(syncId, playerInv),
                Text.literal("Inventory: " + targetPlayer.getName().getString())
            );

            viewer.openHandledScreen(factory);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Failed to open inventory: " + e.getMessage()));
            Essence.getLogger().warn(e, "Error opening /invsee inventory for " + targetPlayer.getName().getString());
            return 0;
        }

        return 1;
    }
}
