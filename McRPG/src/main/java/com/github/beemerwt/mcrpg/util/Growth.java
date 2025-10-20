package com.github.beemerwt.mcrpg.util;

import net.minecraft.block.*;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;

public class Growth {
    public static boolean isMature(BlockState state) {
        Block block = state.getBlock();

        // 1. Directly supported crops
        if (block instanceof CropBlock crop)
            return crop.isMature(state);

        // 2. Special cases
        if (block instanceof CaveVinesBodyBlock || block instanceof CaveVinesHeadBlock)
            return state.getOrEmpty(CaveVines.BERRIES).orElse(false);

        // 3. AGE-based crops (common pattern)
        IntProperty age = getAgeProperty(state);
        if (age != null) {
            int current = state.get(age);
            int max = age.getValues().stream().max(Integer::compare).orElse(0);
            return current >= max;
        }

        // 4. No growth stages → always mature
        return true;
    }

    public static IntProperty getAgeProperty(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof NetherWartBlock)
            return NetherWartBlock.AGE;
        if (block instanceof CocoaBlock)
            return CocoaBlock.AGE;
        if (block instanceof SweetBerryBushBlock)
            return SweetBerryBushBlock.AGE;
        if (block instanceof PitcherCropBlock)
            return PitcherCropBlock.AGE;

        // Generic "age" property fallback
        for (var entry : state.getProperties()) {
            if (entry instanceof IntProperty prop && prop.getName().equals("age"))
                return prop;
        }

        return null;
    }

}
