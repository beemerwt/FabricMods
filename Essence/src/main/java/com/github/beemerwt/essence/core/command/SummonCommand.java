package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.github.beemerwt.essence.core.util.CommandOverrideUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public final class SummonCommand {
    private SummonCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
        CommandOverrideUtil.removeLiteral(dispatcher, "summon");

        dispatcher.register(commandLiteral("essence", "summon").requires(Perms.SUMMON.any())
            .then(CommandManager.argument("entity", RegistryEntryReferenceArgumentType.registryEntry(access, RegistryKeys.ENTITY_TYPE))
                .executes(ctx -> execute(ctx, null, null))
                .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                    .executes(ctx -> execute(ctx,
                        Vec3ArgumentType.getVec3(ctx, "pos"), null))
                    .then(CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound())
                        .executes(ctx -> execute(ctx,
                            Vec3ArgumentType.getVec3(ctx, "pos"),
                            NbtCompoundArgumentType.getNbtCompound(ctx, "nbt")
                        ))
                    )
                )
            )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, @Nullable Vec3d pos, @Nullable NbtCompound nbt) throws CommandSyntaxException {
        var source = ctx.getSource();
        var world = source.getWorld();

        var entityRef = RegistryEntryReferenceArgumentType.getSummonableEntityType(ctx, "entity");

        Identifier entityId = Registries.ENTITY_TYPE.getId(entityRef.value());
        if (!Perms.SUMMON.allowsChild(source, entityId.getPath())) {
            source.sendError(Text.literal("You don't have permission to summon " + entityId));
            return 0;
        }

        // default to executor position if pos was omitted
        Vec3d at = (pos != null) ? pos : source.getPosition();

        var entityType = entityRef.value();

        NbtCompound nbtCompound = (nbt != null) ? nbt : new NbtCompound();

        TypedEntityData<EntityType<?>> entityData;
        Entity entity;
        try {
            entityData = TypedEntityData.create(entityType, nbtCompound);
            entity = entityType.create(world, SpawnReason.COMMAND);
            if (entity == null) throw new IllegalStateException("Entity creation returned null");
        } catch (Exception e) {
            Essence.getLogger().warn(e, "Failed to summon entity {}", entityId);
            source.sendError(Text.literal("Failed to summon entity."));
            return 0;
        }

        // place it first
        entity.refreshPositionAndAngles(at.x, at.y, at.z, entity.getYaw(), entity.getPitch());

        if (nbt != null && !nbt.isEmpty()) {
            try {
                // player is allowed to be null (console), method accepts @Nullable
                EntityType.loadFromEntityNbt(world, source.getPlayer(), entity, entityData);
            } catch (Exception e) {
                source.sendError(Text.literal("Invalid NBT: " + e.getMessage()));
                return 0;
            }
        }

        if (!world.spawnEntity(entity)) {
            source.sendError(Text.literal("Failed to spawn entity into world."));
            return 0;
        }

        source.sendFeedback(() ->
                Text.literal("Summoned ")
                    .append(Text.literal(entity.getStringifiedName()))
                    .append(" at ")
                    .append(Text.literal(String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z))),
            true
        );

        return 1;
    }
}

