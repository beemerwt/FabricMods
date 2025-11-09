package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.LeafBlowerConfig;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Math;

import java.util.Map;
import java.util.function.Supplier;

public class LeafBlower extends PassiveAbility<WoodcuttingConfig, LeafBlowerConfig> {
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

    public LeafBlower(Supplier<LeafBlowerConfig> aConfigSupplier) {
        super(SkillType.WOODCUTTING, aConfigSupplier);
        EventBus.on(AttackBlockEvent.class, this::onAttackBlock);
    }

    private void onAttackBlock(AttackBlockEvent e) {
        if (!config.enabled) return;
        if (!ItemClassifier.isAxe(e.player().getMainHandStack().getItem())) return;

        int level = Leveling.getLevel(e.player(), SkillType.WOODCUTTING);
        if (level < config.minLevel) return;

        var world = e.world();
        var pos = e.pos();
        var state = world.getBlockState(pos);

        Identifier saplingId = LEAF_TO_SAPLING.get(Registries.BLOCK.getId(state.getBlock()));
        if (saplingId == null) return;

        // Special circumstances in which we break the block
        // We do this to not trigger block break or xp events
        // And we award our own items

        var player = e.player();
        if (!world.breakBlock(pos, false, player)) {
            McRPG.getLogger().warning("Leaf Blower failed to break leaf block at {} for {}",
                pos, player.getName().getString());
            return;
        }

        // Drop sapling if succeeds chance roll
        if (Math.random() < (config.saplingDropChance / 100.0)) {
            Item sapling = Registries.ITEM.get(saplingId);
            var itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(sapling));
            itemEntity.setOwner(player.getUuid());
            itemEntity.setPickupDelay(0);
            world.spawnEntity(itemEntity);
        }

        McRPG.getLogger().debug("Leaf Blower broke leaf block at {} for {}",
            pos, player.getName().getString());
    }

    @Override String id() { return "leaf_blower"; }
}
