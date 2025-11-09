package com.github.beemerwt.events.proxy;

import com.github.beemerwt.events.duck.RandomTick;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public final class BlockStateProxy {
    private BlockPos pos;
    private Block block;
    private BlockState state;

    private static final ThreadLocal<BlockStateProxy> TL = ThreadLocal.withInitial(BlockStateProxy::new);

    private BlockStateProxy() {}

    public static BlockStateProxy obtain(
        Block block, BlockState state, BlockPos pos
    ) {
        var proxy = TL.get();
        proxy.pos = pos;
        proxy.state = state;
        proxy.block = block;
        return proxy;
    }

    public BlockPos pos() { return pos; }
    public BlockState state() { return state; }
    public Block block() { return block; }

    public void randomTick(ServerWorld world, Random random) {
        ((RandomTick)state).events$randomTick(state, world, pos, random);
    }

    public static void release() {
        var proxy = TL.get();
        proxy.pos   = null;
        proxy.state = null;
        proxy.block = null;
    }
}
