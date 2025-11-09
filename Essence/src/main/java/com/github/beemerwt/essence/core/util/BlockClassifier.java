package com.github.beemerwt.essence.core.util;

import net.minecraft.block.*;

public final class BlockClassifier {

    public static boolean isContainerBlock(net.minecraft.block.Block block) {
        return block instanceof ChestBlock ||
               block instanceof BarrelBlock ||
               block instanceof AbstractFurnaceBlock ||
               block instanceof DispenserBlock ||
               block instanceof HopperBlock ||
               block instanceof ShulkerBoxBlock;
    }
}
