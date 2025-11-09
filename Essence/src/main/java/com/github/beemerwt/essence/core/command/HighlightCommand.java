package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.BlockClassifier;
import com.github.beemerwt.essence.core.util.HighlightEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.server.command.CommandManager.argument;

public class HighlightCommand {
    private static final int HIGHLIGHT_SECONDS = 8;

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        // Command registration logic goes here
        d.register(commandLiteral("essence", "highlight").requires(Perms.HIGHLIGHT)
            .then(argument("target", BlockPosArgumentType.blockPos())
                .executes(ctx -> HighlightEntity.highlightForPlayer(
                    ctx.getSource(),
                    BlockPosArgumentType.getLoadedBlockPos(ctx, "target"),
                    HIGHLIGHT_SECONDS)
                )

                .then(argument("seconds", IntegerArgumentType.integer(1))
                    .executes(ctx -> HighlightEntity.highlightForPlayer(
                        ctx.getSource(),
                        BlockPosArgumentType.getLoadedBlockPos(ctx, "target"),
                        IntegerArgumentType.getInteger(ctx, "seconds"))
                    )
                )
            )

            .executes(ctx ->
                HighlightEntity.clearAllForPlayer(ctx.getSource().getServer(),
                    ctx.getSource().getPlayerOrThrow().getUuid()))
        );
    }
}
