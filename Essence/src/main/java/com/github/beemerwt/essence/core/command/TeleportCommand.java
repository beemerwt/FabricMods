package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.data.LocationType;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.CommandOverrideUtil;
import com.github.beemerwt.essence.core.util.Locations;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.argument.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.LookTarget;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Identical to the vanilla tp command except it saves /back for teleported players.
 */
public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.teleport.invalidPosition"));

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        // Remove vanilla teleport commands
        CommandOverrideUtil.removeLiteral(d, "tp");
        CommandOverrideUtil.removeLiteral(d, "teleport");

        LiteralCommandNode<ServerCommandSource> node = d.register(
            commandLiteral("essence", "teleport").requires(Perms.TP)
                // /teleport <location>
                .then(argument("location", Vec3ArgumentType.vec3())
                    .executes(ctx ->
                        execute(
                            ctx.getSource(),
                            Collections.singleton(ctx.getSource().getEntityOrThrow()),
                            ctx.getSource().getWorld(),
                            Vec3ArgumentType.getPosArgument(ctx, "location"),
                            null,
                            null
                        )
                    )
                )

                // /teleport <destination>
                .then(argument("destination", EntityArgumentType.entity())
                    .executes(ctx ->
                        execute(
                            ctx.getSource(),
                            Collections.singleton(ctx.getSource().getEntityOrThrow()),
                            EntityArgumentType.getEntity(ctx, "destination")
                        )
                    )
                )

                // /teleport <targets> ...
                .then(argument("targets", EntityArgumentType.entities())
                    // /teleport <targets> <location>
                    .then(argument("location", Vec3ArgumentType.vec3())
                        .executes(ctx ->
                            execute(
                                ctx.getSource(),
                                EntityArgumentType.getEntities(ctx, "targets"),
                                ctx.getSource().getWorld(),
                                Vec3ArgumentType.getPosArgument(ctx, "location"),
                                null,
                                null
                            )
                        )

                        // /teleport <targets> <location> <rotation>
                        .then(argument("rotation", RotationArgumentType.rotation())
                            .executes(ctx ->
                                execute(
                                    ctx.getSource(),
                                    EntityArgumentType.getEntities(ctx, "targets"),
                                    ctx.getSource().getWorld(),
                                    Vec3ArgumentType.getPosArgument(ctx, "location"),
                                    RotationArgumentType.getRotation(ctx, "rotation"),
                                    null
                                )
                            )
                        )

                        // /teleport <targets> <location> facing ...
                        .then(literal("facing")
                            // ... entity <facingEntity> [facingAnchor]
                            .then(literal("entity")
                                .then(argument("facingEntity", EntityArgumentType.entity())
                                    .executes(ctx ->
                                        execute(
                                            ctx.getSource(),
                                            EntityArgumentType.getEntities(ctx, "targets"),
                                            ctx.getSource().getWorld(),
                                            Vec3ArgumentType.getPosArgument(ctx, "location"),
                                            null,
                                            new LookTarget.LookAtEntity(
                                                EntityArgumentType.getEntity(ctx, "facingEntity"),
                                                EntityAnchorArgumentType.EntityAnchor.FEET
                                            )
                                        )
                                    )
                                    .then(argument("facingAnchor", EntityAnchorArgumentType.entityAnchor())
                                        .executes(ctx ->
                                            execute(
                                                ctx.getSource(),
                                                EntityArgumentType.getEntities(ctx, "targets"),
                                                ctx.getSource().getWorld(),
                                                Vec3ArgumentType.getPosArgument(ctx, "location"),
                                                null,
                                                new LookTarget.LookAtEntity(
                                                    EntityArgumentType.getEntity(ctx, "facingEntity"),
                                                    EntityAnchorArgumentType.getEntityAnchor(ctx, "facingAnchor")
                                                )
                                            )
                                        )
                                    )
                                )
                            )

                            // ... <facingLocation>
                            .then(argument("facingLocation", Vec3ArgumentType.vec3())
                                .executes(ctx ->
                                    execute(
                                        ctx.getSource(),
                                        EntityArgumentType.getEntities(ctx, "targets"),
                                        ctx.getSource().getWorld(),
                                        Vec3ArgumentType.getPosArgument(ctx, "location"),
                                        null,
                                        new LookTarget.LookAtPosition(
                                            Vec3ArgumentType.getVec3(ctx, "facingLocation")
                                        )
                                    )
                                )
                            )
                        )
                    )

                    // /teleport <targets> <destination>
                    .then(
                        argument("destination", EntityArgumentType.entity())
                            .executes(ctx ->
                                execute(
                                    ctx.getSource(),
                                    EntityArgumentType.getEntities(ctx, "targets"),
                                    EntityArgumentType.getEntity(ctx, "destination")
                                )
                            )
                    )
                )
        );

        d.register(commandLiteral("essence", "tp").requires(Perms.TP).redirect(node));
    }

    private static int execute(ServerCommandSource source, Collection<? extends Entity> targets, Entity destination) throws CommandSyntaxException {
        for (Entity entity : targets) {
            teleport(source, entity, (ServerWorld) destination.getEntityWorld(),
                destination.getX(), destination.getY(), destination.getZ(),
                EnumSet.noneOf(PositionFlag.class), destination.getYaw(), destination.getPitch(), (LookTarget) null);
        }

        if (targets.size() == 1) {
            source.sendFeedback(() -> Text.translatable("commands.teleport.success.entity.single", targets.iterator().next().getDisplayName(), destination.getDisplayName()), true);
        } else {
            source.sendFeedback(() -> Text.translatable("commands.teleport.success.entity.multiple", targets.size(), destination.getDisplayName()), true);
        }

        return targets.size();
    }

    private static int execute(ServerCommandSource source, Collection<? extends Entity> targets, ServerWorld world, PosArgument location, @Nullable PosArgument rotation, @Nullable LookTarget facingLocation) throws CommandSyntaxException {
        Vec3d vec3d = location.getPos(source);
        Vec2f vec2f = rotation == null ? null : rotation.getRotation(source);

        for (Entity entity : targets) {
            Set<PositionFlag> set = getFlags(location, rotation, entity.getEntityWorld().getRegistryKey() == world.getRegistryKey());
            if (vec2f == null) {
                teleport(source, entity, world, vec3d.x, vec3d.y, vec3d.z, set, entity.getYaw(), entity.getPitch(), facingLocation);
            } else {
                teleport(source, entity, world, vec3d.x, vec3d.y, vec3d.z, set, vec2f.y, vec2f.x, facingLocation);
            }
        }

        if (targets.size() == 1) {
            source.sendFeedback(() -> Text.translatable("commands.teleport.success.location.single", targets.iterator().next().getDisplayName(), formatFloat(vec3d.x), formatFloat(vec3d.y), formatFloat(vec3d.z)), true);
        } else {
            source.sendFeedback(() -> Text.translatable("commands.teleport.success.location.multiple", targets.size(), formatFloat(vec3d.x), formatFloat(vec3d.y), formatFloat(vec3d.z)), true);
        }

        return targets.size();
    }

    private static Set<PositionFlag> getFlags(PosArgument pos, @Nullable PosArgument rotation, boolean sameDimension) {
        Set<PositionFlag> set = PositionFlag.ofDeltaPos(pos.isXRelative(), pos.isYRelative(), pos.isZRelative());
        Set<PositionFlag> set2 = sameDimension ? PositionFlag.ofPos(pos.isXRelative(), pos.isYRelative(), pos.isZRelative()) : Set.of();
        Set<PositionFlag> set3 = rotation == null ? PositionFlag.ROT : PositionFlag.ofRot(rotation.isYRelative(), rotation.isXRelative());
        return PositionFlag.combine(set, set2, set3);
    }

    private static String formatFloat(double d) {
        return String.format(Locale.ROOT, "%f", d);
    }

    private static void teleport(ServerCommandSource source, Entity target, ServerWorld world, double x, double y, double z, Set<PositionFlag> movementFlags, float yaw, float pitch, @Nullable LookTarget facingLocation) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.ofFloored(x, y, z);

        if (!World.isValid(blockPos)) {
            throw INVALID_POSITION_EXCEPTION.create();
        } else {
            double d = movementFlags.contains(PositionFlag.X) ? x - target.getX() : x;
            double e = movementFlags.contains(PositionFlag.Y) ? y - target.getY() : y;
            double f = movementFlags.contains(PositionFlag.Z) ? z - target.getZ() : z;
            float g = movementFlags.contains(PositionFlag.Y_ROT) ? yaw - target.getYaw() : yaw;
            float h = movementFlags.contains(PositionFlag.X_ROT) ? pitch - target.getPitch() : pitch;
            float i = MathHelper.wrapDegrees(g);
            float j = MathHelper.wrapDegrees(h);

            if (target instanceof ServerPlayerEntity player) {
                var current = Locations.capture(player);
                if (!Essence.getLocationStore().setSingle(player.getUuid(), LocationType.BACK, current))
                    Essence.getLogger().warning("Failed to save back location for {}", player.getStringifiedName());
            }

            if (target.teleport(world, d, e, f, movementFlags, i, j, true)) {
                if (facingLocation != null) {
                    facingLocation.look(source, target);
                }

                label46:
                {
                    if (target instanceof LivingEntity livingEntity) {
                        if (livingEntity.isGliding()) {
                            break label46;
                        }
                    }

                    target.setVelocity(target.getVelocity().multiply(1.0F, 0.0F, 1.0F));
                    target.setOnGround(true);
                }

                if (target instanceof PathAwareEntity pathAwareEntity) {
                    pathAwareEntity.getNavigation().stop();
                }

            }
        }
    }
}
