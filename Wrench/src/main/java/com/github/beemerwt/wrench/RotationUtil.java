package com.github.beemerwt.wrench;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

import java.util.List;

public final class RotationUtil {
    private RotationUtil() {}

    // Global orders
    private static final List<Direction> RING = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final List<Direction> ORDER_6 = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN);
    private static final List<Direction.Axis> AXIS_ORDER = List.of(Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y);

    public static BlockState rotate(BlockState state, boolean reverse) {
        // Horizontal-only (stairs, furnaces, observers, etc.)
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            Direction cur = state.get(Properties.HORIZONTAL_FACING);
            Direction next = cycle(RING, cur, reverse);
            return state.with(Properties.HORIZONTAL_FACING, next);
        }

        // Hopper: DOWN + horizontals (no UP)
        if (state.contains(Properties.HOPPER_FACING)) {
            List<Direction> allowed = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN);
            Direction cur = state.get(Properties.HOPPER_FACING);
            Direction next = cycle(allowed, cur, reverse);
            return state.with(Properties.HOPPER_FACING, next);
        }

        // Full 6-way
        if (state.contains(Properties.FACING)) {
            Direction cur = state.get(Properties.FACING);
            Direction next = cycle(ORDER_6, cur, reverse);
            return state.with(Properties.FACING, next);
        }

        // Axis (logs, pillars, chains, etc.)
        if (state.contains(Properties.AXIS)) {
            Direction.Axis cur = state.get(Properties.AXIS);
            Direction.Axis next = cycle(AXIS_ORDER, cur, reverse);
            return state.with(Properties.AXIS, next);
        }

        // 0..15 rotation (signs, item frames’ backing blocks etc.)
        if (state.getProperties().contains(Properties.ROTATION)) {
            IntProperty rotProp = Properties.ROTATION; // 0..15
            int cur = state.get(rotProp);
            int step = reverse ? -1 : 1;
            int next = Math.floorMod(cur + step, 16);
            return state.with(rotProp, next);
        }

        // Fallback: try vanilla rotate (many blocks implement it)
        return state.rotate(reverse ? BlockRotation.COUNTERCLOCKWISE_90 : BlockRotation.CLOCKWISE_90);
    }

    private static <T> T cycle(List<T> list, T cur, boolean reverse) {
        int start = list.indexOf(cur);
        if (start < 0) start = 0;
        int idx = Math.floorMod(start + (reverse ? -1 : 1), list.size());
        return list.get(idx);
    }
}
