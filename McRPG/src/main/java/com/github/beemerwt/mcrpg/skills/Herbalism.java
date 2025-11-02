package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.events.CropBlockEvents;
import com.github.beemerwt.events.PlayerEvents;
import com.github.beemerwt.events.proxy.CropBlockProxy;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.abilities.DoubleDrops;
import com.github.beemerwt.mcrpg.abilities.GreenTerra;
import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.util.Growth;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import java.util.List;

public class Herbalism {

    public static void register() {
        CropBlockEvents.RANDOM_TICK.register(Herbalism::onRandomTick);
        PlayerBlockBreakEvents.AFTER.register(Herbalism::onCropBroken);
        PlayerEvents.BLOCK_PLACED.register(Herbalism::onBlockPlaced);
    }

    private static void onBlockPlaced(
        World world, @Nullable LivingEntity placer, BlockPos pos, BlockState state, ItemStack stack
    ) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!(placer instanceof ServerPlayerEntity sp)) return;

        var block = state.getBlock();
        var id = Registries.BLOCK.getId(block).toString();
        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);

        // TODO: Mark crops that don't have an age with -1 modifier
        //       So we can ignore them when awarding xp

        int level = Leveling.getLevel(sp, SkillType.HERBALISM); // ensure level is cached
        float growthModifier = Leveling.getScaled(cfg.greenThumb.baseGrowthMultiplier,
            cfg.greenThumb.maxGrowthMultiplier, level);

        // TODO: We keep tracked block state for the herbs as well
        //      So we can avoid awarding xp for player-placed crops that don't have age

        var cm = CropMarkers.get(sw);
        cm.mark(sw, pos, growthModifier);
        cm.mark(sw, pos.down(), growthModifier);

        long k = pos.asLong();
        boolean hasHere  = cm.containsKey(k);
        boolean hasBelow = cm.containsKey(pos.down().asLong());

        McRPG.getLogger().debug("onPlaced: world={} wHash={} cmHash={} size={}" +
                "pos={} long={} wrote={} hasHere={} hasBelow={}",
            sw.getRegistryKey().getValue(),
            System.identityHashCode(sw),
            System.identityHashCode(cm),
            cm.size(),
            pos, k, growthModifier,
            hasHere, hasBelow
        );
    }

    private static void onRandomTick(ServerWorld world, CropBlockProxy proxy, Random random) {
        float m = CropMarkers.get(world).getMultiplier(proxy.pos());
        if (m <= 1.0f) return;

        int extra = (int) Math.floor(m - 1.0f);
        float frac = (m - 1.0f) - extra;
        for (int i = 0; i < extra; i++)
            proxy.randomTick(world, random);

        if (random.nextFloat() < frac)
            proxy.randomTick(world, random);
    }

    private static void onCropBroken(
        World world, PlayerEntity p, BlockPos pos, BlockState state, @Nullable BlockEntity entity
    ) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!(p instanceof ServerPlayerEntity player)) return;

        HerbalismConfig cfg = ConfigManager.getSkillConfig(SkillType.HERBALISM);
        var blocks = cfg.getBlocks();
        var block = state.getBlock();

        long blockXp = Leveling.resolveBlockXp(blocks, block);
        if (blockXp <= 0) return;

        if (!Growth.isMature(state)) return; // Only award XP for fully grown crops

        ItemStack tool = player.getMainHandStack();
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, entity, player, tool);

        // GREEN TERRA: queue a replant candidate while active
        GreenTerra.considerReplant(player, sw, pos, state);

        int level = Leveling.getLevel(player, SkillType.HERBALISM);
        var id = Registries.BLOCK.getId(block);

        // Only trigger skills if the player is using a hoe and the crop supports double drops
        if (cfg.doubleDropCrops.get(id.toString()) != null)
            if (DoubleDrops.processTrigger(cfg.doubleDrops, level, player.getEntityWorld(), pos, block, drops))
                blockXp *= 2; // Double the XP awarded

        // Apply per-skill modifier
        double mod = cfg.xpModifier;
        long awarded = org.joml.Math.max(0, Math.round(blockXp * mod));
        if (awarded <= 0) return;

        McRPG.getLogger().debug("{} Herbalism XP awarded to {} for crop broken {}",
                awarded, player.getName(), block);
        Leveling.addXp(player, SkillType.HERBALISM, awarded);
    }
}
