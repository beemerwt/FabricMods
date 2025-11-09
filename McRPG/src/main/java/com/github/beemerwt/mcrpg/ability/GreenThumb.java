package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.events.BlockEvents;
import com.github.beemerwt.events.proxy.BlockStateProxy;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.ability.GreenThumbConfig;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.event.PlaceBlockEvent;
import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.Growth;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.joml.Math;

import java.util.function.Supplier;

public class GreenThumb extends PassiveAbility<HerbalismConfig, GreenThumbConfig> {
    public GreenThumb(Supplier<GreenThumbConfig> aConfigSupplier) {
        super(SkillType.HERBALISM, aConfigSupplier);
        EventBus.subscribe(PlaceBlockEvent.class, this::onPlaceBlock);
        EventBus.subscribe(BreakBlockEvent.class, this::onBreakBlock);
        BlockEvents.RANDOM_TICK.register(this::onRandomTick);
    }

    private void onBreakBlock(BreakBlockEvent e) {
        if (!Growth.isApplicableBlock(e.state())) return;
        var cm = CropMarkers.get(e.world());

        if (!Growth.isVerticalStacker(e.block())) {
            cm.unmark(e.pos());
            return;
        }

        BlockPos newHead = e.pos().down();
        BlockState newHeadState = e.world().getBlockState(newHead);

        // Attempt to find the old head position
        BlockPos oldHead = e.pos();
        BlockState oldHeadState = e.state();
        while (!Growth.isVerticalHead(e.world(), oldHead, oldHeadState)) {
            oldHead = oldHead.up();
            oldHeadState = e.world().getBlockState(oldHead);
        }

        cm.unmark(oldHead);

        // Mark the new head (if it exists) with the updated multiplier
        if (Growth.isVerticalStacker(newHeadState.getBlock())) {
            int level = Leveling.getLevel(e.player(), SkillType.HERBALISM); // ensure level is cached
            float m = Leveling.getScaled(config.baseGrowthMultiplier, config.maxGrowthMultiplier, level);
            cm.mark(newHead, m);

            McRPG.getLogger().debug("Vertical stacker head changed: world={} newHead={} mul={}",
                e.world().getRegistryKey().getValue(), newHead);
            return;
        }

        McRPG.getLogger().debug("Vertical stacker head changed: world={}, no new head",
            e.world().getRegistryKey().getValue());
    }

    @Override protected String id() { return "mcrpg.ability.green_thumb"; }

    private void onRandomTick(ServerWorld world, BlockStateProxy proxy, Random random) {
        var cm = CropMarkers.get(world);
        float m = cm.getMultiplier(proxy.pos());
        if (m <= 1.0f) return;

        McRPG.getLogger().debug("RandomTick GreenThumb: world={} pos={} mul={}",
            world.getRegistryKey().getValue(), proxy.pos(), m);

        var isVertical = Growth.isVerticalStacker(proxy.block());

        int extra = (int) Math.floor(m - 1.0f);
        float frac = (m - 1.0f) - extra;
        BlockPos pos = proxy.pos();
        for (int i = 0; i < extra; i++) {
            proxy.randomTick(world, random);

            if (isVertical) {
                var newPos = getNewPosAfterGrowth(world, pos);
                if (!newPos.equals(pos)) {
                    cm.unmark(pos);
                    cm.mark(newPos, m);
                }

                pos = newPos;
            }
        }

        if (random.nextFloat() < frac) {
            proxy.randomTick(world, random);

            if (isVertical) {
                var newPos = getNewPosAfterGrowth(world, pos);
                if (!newPos.equals(pos)) {
                    cm.unmark(pos);
                    cm.mark(newPos, m);
                }
            }
        }
    }

    private BlockPos getNewPosAfterGrowth(ServerWorld world, BlockPos pos) {
        var abovePos = pos.up();
        var aboveState = world.getBlockState(abovePos);
        if (Growth.isVerticalHead(world, abovePos, aboveState)) {
            return abovePos;
        }
        return pos;
    }

    private void onPlaceBlock(PlaceBlockEvent e) {
        if (!e.state().hasRandomTicks()) return;

        // TODO: Mark crops that don't have an age with -1 modifier
        //       So we can ignore them when awarding xp

        // TODO: We keep tracked block state for the herbs as well
        //      So we can avoid awarding xp for player-placed crops that don't have age

        int level = Leveling.getLevel(e.player(), SkillType.HERBALISM); // ensure level is cached
        float mul = Leveling.getScaled(config.baseGrowthMultiplier, config.maxGrowthMultiplier, level);

        var cm = CropMarkers.get(e.world());
        cm.mark(e.pos(), mul);

        McRPG.getLogger().debug("GreenThumb: world={} pos={} mul={}",
            e.world().getRegistryKey().getValue(), e.pos(), mul);
    }
}
