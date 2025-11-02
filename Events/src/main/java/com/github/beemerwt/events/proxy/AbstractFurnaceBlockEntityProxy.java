package com.github.beemerwt.events.proxy;

import com.github.beemerwt.events.duck.FuelTime;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class AbstractFurnaceBlockEntityProxy {

    private ServerWorld world;
    private BlockPos pos;
    private BlockState state;
    private AbstractFurnaceBlockEntity entity;

    private static final ThreadLocal<AbstractFurnaceBlockEntityProxy> TL
            = ThreadLocal.withInitial(AbstractFurnaceBlockEntityProxy::new);

    private AbstractFurnaceBlockEntityProxy() {}

    public static AbstractFurnaceBlockEntityProxy obtain(
            BlockState state, BlockPos pos, ServerWorld world, AbstractFurnaceBlockEntity entity
    ) {
        var proxy = TL.get();
        proxy.pos = pos;
        proxy.state = state;
        proxy.world = world;
        proxy.entity = entity;
        return proxy;
    }

    public BlockPos pos() { return pos; }
    public BlockState state() { return state; }
    public AbstractFurnaceBlockEntity entity() { return entity; }
    public ServerWorld world() { return world; }

    public void addFuelTime(int time) {
        ((FuelTime)entity).addFuelTime(time);
    }

    public int getFuelTime() {
        return ((FuelTime)entity).getFuelTime();
    }

    public static void release() {
        var proxy = TL.get();
        proxy.pos   = null;
        proxy.state = null;
        proxy.entity = null;
        proxy.world = null;
    }
}
