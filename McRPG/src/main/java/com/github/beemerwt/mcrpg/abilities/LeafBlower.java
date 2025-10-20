package com.github.beemerwt.mcrpg.abilities;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.Map;

public class LeafBlower {
    private static final Map<Identifier, Identifier> LEAF_TO_SAPLING = Map.ofEntries(
        Map.entry(Identifier.of("minecraft:oak_leaves"), Identifier.of("minecraft:oak_sapling")),
        Map.entry(Identifier.of("minecraft:spruce_leaves"), Identifier.of("minecraft:spruce_sapling")),
        Map.entry(Identifier.of("minecraft:birch_leaves"), Identifier.of("minecraft:birch_sapling")),
        Map.entry(Identifier.of("minecraft:jungle_leaves"), Identifier.of("minecraft:jungle_sapling")),
        Map.entry(Identifier.of("minecraft:acacia_leaves"), Identifier.of("minecraft:acacia_sapling")),
        Map.entry(Identifier.of("minecraft:dark_oak_leaves"), Identifier.of("minecraft:dark_oak_sapling")),
        Map.entry(Identifier.of("minecraft:mangrove_leaves"), Identifier.of("minecraft:mangrove_propagule")),
        Map.entry(Identifier.of("minecraft:azalea_leaves"), Identifier.of("minecraft:azalea_sapling")),
        Map.entry(Identifier.of("minecraft:flowering_azalea_leaves"), Identifier.of("minecraft:azalea_sapling")),
        Map.entry(Identifier.of("minecraft:cherry_leaves"), Identifier.of("minecraft:cherry_sapling"))
    );

    private LeafBlower() {}

    public static void processBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        WoodcuttingConfig cfg = ConfigManager.getSkillConfig(SkillType.WOODCUTTING);
        if (!cfg.leafBlower.enabled) return;

        if (!ItemClassifier.isAxe(player.getMainHandStack().getItem())) return;

        Identifier saplingId = LEAF_TO_SAPLING.get(Registries.BLOCK.getId(state.getBlock()));
        Item sapling = Registries.ITEM.get(saplingId);

        int level = Leveling.getLevel(player, SkillType.WOODCUTTING);
        if (level < cfg.leafBlower.minLevel) return;

        if (!world.breakBlock(pos, false, player)) {
            McRPG.getLogger().warning("Leaf Blower failed to break leaf block at {} for {}",
                    pos, player.getName().getString());
            return;
        }

        // Drop sapling if succeeds chance roll
        if (Math.random() < (cfg.leafBlower.saplingDropChance / 100.0)) {
            var itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(sapling));
            itemEntity.setOwner(player.getUuid());
            itemEntity.setPickupDelay(0);
            world.spawnEntity(itemEntity);
        }

        McRPG.getLogger().debug("Leaf Blower broke leaf block at {} for {}",
                pos, player.getName().getString());
    }
}
