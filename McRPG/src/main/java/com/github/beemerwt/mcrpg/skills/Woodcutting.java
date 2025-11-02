package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.WoodcuttingConfig;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.abilities.DoubleDrops;
import com.github.beemerwt.mcrpg.abilities.LeafBlower;
import com.github.beemerwt.mcrpg.abilities.TreeFeller;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import java.util.List;

public class Woodcutting {
    private Woodcutting() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(Woodcutting::onLogChopped);
    }

    public static void onStartBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (!BlockClassifier.isLeaf(state.getBlock())) return;
        LeafBlower.processBlock(player, world, pos, state);
    }

    public static void onLogChopped(World world,
                                    PlayerEntity p,
                                    BlockPos pos,
                                    BlockState state,
                                    @Nullable BlockEntity entity)
    {
        if (!(world instanceof ServerWorld sw)) return;
        if (!(p instanceof ServerPlayerEntity player)) return;
        if (PlacedBlockTracker.get(sw).isMarked(pos)) return; // Ignore player-placed blocks

        WoodcuttingConfig cfg = ConfigManager.getSkillConfig(SkillType.WOODCUTTING);
        var block = state.getBlock();
        long blockXp = Leveling.resolveBlockXp(cfg.getBlocks(), block);
        if (blockXp <= 0) return;

        ItemStack tool = player.getMainHandStack();
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, entity, player, tool);

        int level = Leveling.getLevel(player, SkillType.WOODCUTTING);

        // Only trigger abilities when using an axe
        if (ItemClassifier.isAxe(player.getMainHandStack().getItem())) {
            if (AbilityManager.isActive(player, ActiveAbilityType.TREE_FELLER)) {
                long totalFromFeller = TreeFeller.fellAndProcess(player, pos, level, cfg);
                if (totalFromFeller <= 0) return;

                long awarded = Math.max(0, Math.round(totalFromFeller * cfg.xpModifier));
                if (awarded <= 0) return;

                McRPG.getLogger().debug("{} Woodcutting XP (Tree Feller) -> {}",
                        awarded, player.getName().getString());
                Leveling.addXp(player, SkillType.WOODCUTTING, awarded);
                return; // IMPORTANT: don't double-count the original block
            } else {
                // Normal single-block double drops
                if (DoubleDrops.processTrigger(cfg.doubleDrops, level, sw, pos, block, drops)) {
                    blockXp *= 2;
                }
            }
        }

        long awarded = Math.max(0, Math.round(blockXp * cfg.xpModifier));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Woodcutting XP awarded to {} for {}",
                awarded, player.getName().getString(), block);
        Leveling.addXp(player, SkillType.WOODCUTTING, awarded);
    }
}
