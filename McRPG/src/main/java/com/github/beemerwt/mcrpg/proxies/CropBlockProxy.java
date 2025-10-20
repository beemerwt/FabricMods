package com.github.beemerwt.mcrpg.proxies;

import com.github.beemerwt.mcrpg.extension.RandomTickExtension;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public final class CropBlockProxy {
    private BlockPos pos;
    private BlockState state;
    private CropBlock block;

    private static final ThreadLocal<CropBlockProxy> TL = ThreadLocal.withInitial(CropBlockProxy::new);

    private CropBlockProxy() {}

    public static CropBlockProxy obtain(
            BlockState state, BlockPos pos, CropBlock block
    ) {
        var proxy = TL.get();
        proxy.pos = pos;
        proxy.state = state;
        proxy.block = block;
        return proxy;
    }

    public BlockPos pos() { return pos; }
    public BlockState state() { return state; }
    public CropBlock block() { return block; }

    public void randomTick(ServerWorld world, Random random) {
        ((RandomTickExtension)block).mcrpg$vanillaRandomTick(state, world, pos, random);
    }

    public static void release() {
        var proxy = TL.get();
        proxy.pos   = null;
        proxy.state = null;
        proxy.block = null;
    }
}
