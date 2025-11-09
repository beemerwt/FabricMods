package com.github.beemerwt.mcrpg.util;

import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

// imports (keep what you already have)
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class Growth {

    private Growth() {}

    /* ---------------------- Maturity / Age helpers ---------------------- */
    public static boolean shouldAwardXpOnBreak(ServerWorld world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();

        // Stage-in-place: award only if mature (even if originally placed)
        if (isStageGrower(state)) {
            return isStageGrowerMature(state);
        }

        // Vertical stackers: award only for segments not marked as player-placed
        if (isVerticalStacker(b)) {
            return !PlacedBlockTracker.get(world).isMarked(pos);
        }

        // Fruits: award only if the fruit block itself wasn’t placed
        if (isFruitBlock(b)) {
            return !PlacedBlockTracker.get(world).isMarked(pos);
        }

        // Sea pickles: no random-tick growth; you probably don’t want GreenThumb XP here.
        if (b == Blocks.SEA_PICKLE) return false;

        // Default conservative
        return !PlacedBlockTracker.get(world).isMarked(pos);
    }

    /** Call this from your mixins when a vertical head grows a new segment upward to propagate ownership. */
    public static void propagateVerticalOwnership(ServerWorld world, BlockPos oldHead, BlockPos newHead) {
        var cm = CropMarkers.get(world);
        var multiplier = cm.getMultiplier(oldHead);
        cm.mark(newHead, multiplier);
    }

    /** True iff this block is a “stage-in-place” grower (ages in its own state). */
    public static boolean isStageGrower(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CropBlock
            || b == Blocks.NETHER_WART
            || b == Blocks.COCOA
            || b == Blocks.SWEET_BERRY_BUSH
            || b == Blocks.TORCHFLOWER_CROP   // 1.20/1.21 crop, AGE_1
            || b instanceof PitcherCropBlock; // 1.20/1.21, AGE_2
    }

    /** True iff this stage-in-place grower is mature (ignore non-stage blocks). */
    public static boolean isStageGrowerMature(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock c) return c.isMature(state);
        if (b == Blocks.NETHER_WART) return state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (b == Blocks.COCOA)       return state.get(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;
        if (b == Blocks.SWEET_BERRY_BUSH) return state.get(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
        // 1.21+: Torchflower **crop** is AGE_1 (0..1), mature at 1
        if (b == Blocks.TORCHFLOWER_CROP) return state.get(Properties.AGE_1) == 1;
        // 1.20+: PitcherCropBlock is AGE_2 (0..2), mature at 2
        if (b instanceof PitcherCropBlock) return state.get(Properties.AGE_2) == 2;
        return false;
    }

    /** Return the “AGE_*” property if this block has one of the standard age props, else null. */
    public static @Nullable IntProperty getAgeProperty(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock)         return CropBlock.AGE;
        if (b == Blocks.NETHER_WART)        return NetherWartBlock.AGE;
        if (b == Blocks.COCOA)              return CocoaBlock.AGE;
        if (b == Blocks.SWEET_BERRY_BUSH)   return SweetBerryBushBlock.AGE;
        if (b instanceof PitcherCropBlock)  return PitcherCropBlock.AGE;
        if (b == Blocks.TORCHFLOWER_CROP)   return Properties.AGE_1; // 0..1
        // many “AGE” props exist for decorative/fading blocks; don’t treat those as crops
        return null;
    }

    /* ---------------------- Vertical growers & fruits ---------------------- */

    /** True iff this block is a vertical stacker family (growth by adding/removing neighbor segment). */
    public static boolean isVerticalStacker(Block b) {
        // Prefer block equality for vanilla; keep class checks for 1.21 vine refactors
        if (b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO) return true;
        if (b == Blocks.KELP || b == Blocks.KELP_PLANT) return true;

        // Vines (1.20 names) still exist as block constants in 1.21; classes changed under the hood.
        if (b == Blocks.WEEPING_VINES || b == Blocks.WEEPING_VINES_PLANT) return true;
        if (b == Blocks.TWISTING_VINES || b == Blocks.TWISTING_VINES_PLANT) return true;
        if (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT) return true;

        // Chorus tower also grows vertically from flower head
        if (b == Blocks.CHORUS_PLANT || b == Blocks.CHORUS_FLOWER) return true;

        return false;
    }

    /** True iff this block is a fruit block (melon/pumpkin) — not a grower by itself. */
    public static boolean isFruitBlock(Block b) {
        return b == Blocks.MELON || b == Blocks.PUMPKIN;
    }

    public static boolean isFruitStem(Block b) {
        return b.equals(Blocks.MELON_STEM) || b.equals(Blocks.PUMPKIN_STEM);
    }

    /** True iff this state is currently the *active head* of a vertical grower. */
    public static boolean isVerticalHead(ServerWorld world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();

        if (b == Blocks.SUGAR_CANE) {
            return world.isAir(pos.up());
        }
        if (b == Blocks.CACTUS) {
            return world.isAir(pos.up());
        }
        if (b == Blocks.BAMBOO) {
            // Head if above is air (tip grows into air)
            return world.isAir(pos.up());
        }
        if (b == Blocks.KELP) {
            // Kelp head is KELP with water above (body is KELP_PLANT)
            return world.getBlockState(pos.up()).isOf(Blocks.WATER);
        }

        if (state.isOf(Blocks.CAVE_VINES)) {
            return true; // head is the CAVE_VINES block itself
        }

        // Twisting/weeping vines: head is the non-*_PLANT block
        if (b == Blocks.TWISTING_VINES || b == Blocks.WEEPING_VINES) return true;

        if (b == Blocks.CHORUS_FLOWER) {
            Integer age = state.get(ChorusFlowerBlock.AGE);
            return age != null && age < 5; // 0..4 grows, 5 becomes CHORUS_PLANT
        }

        return false;
    }

    public static boolean isApplicableBlock(BlockState state) {
        Block b = state.getBlock();
        return isStageGrower(state)
            || isVerticalStacker(b)
            || isFruitStem(b);
    }

    /* ---------------------- Placement targeting for GreenThumb ---------------------- */

    /**
     * Return the GreenThumb “kind” to track for a newly placed block, or null if we should ignore it.
     * This mirrors your GreenThumb.Kind without importing it here.
     */
    public enum Kind { NONE, STAGE_IN_PLACE, VERTICAL_HEAD, STEM_ORIGIN }

    public static @Nullable Kind kindForPlacement(BlockState state) {
        Block b = state.getBlock();

        // Stage-in-place growers (age on the same block)
        if (isStageGrower(state)) return Kind.STAGE_IN_PLACE;

        // Vertical heads (the placed block is already a head)
        if (b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO
            || b == Blocks.KELP || b == Blocks.WEEPING_VINES || b == Blocks.TWISTING_VINES
            || b == Blocks.CAVE_VINES || b == Blocks.CHORUS_FLOWER) {
            return Kind.VERTICAL_HEAD;
        }

        // Melon/pumpkin **stems** are the growth origin
        if (b instanceof StemBlock) return Kind.STEM_ORIGIN;

        return null;
    }
}

