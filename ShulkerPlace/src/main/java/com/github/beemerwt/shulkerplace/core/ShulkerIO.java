package com.github.beemerwt.shulkerplace.core;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

public final class ShulkerIO {
    private static final int SHULKER_SIZE = 27;

    private ShulkerIO() {}

    public static boolean isShulker(ItemStack stack) {
        return stack.getItem().equals(Items.SHULKER_BOX)
            || stack.getItem().equals(Items.WHITE_SHULKER_BOX)
            || stack.getItem().equals(Items.ORANGE_SHULKER_BOX)
            || stack.getItem().equals(Items.MAGENTA_SHULKER_BOX)
            || stack.getItem().equals(Items.LIGHT_BLUE_SHULKER_BOX)
            || stack.getItem().equals(Items.YELLOW_SHULKER_BOX)
            || stack.getItem().equals(Items.LIME_SHULKER_BOX)
            || stack.getItem().equals(Items.PINK_SHULKER_BOX)
            || stack.getItem().equals(Items.GRAY_SHULKER_BOX)
            || stack.getItem().equals(Items.LIGHT_GRAY_SHULKER_BOX)
            || stack.getItem().equals(Items.CYAN_SHULKER_BOX)
            || stack.getItem().equals(Items.PURPLE_SHULKER_BOX)
            || stack.getItem().equals(Items.BLUE_SHULKER_BOX)
            || stack.getItem().equals(Items.BROWN_SHULKER_BOX)
            || stack.getItem().equals(Items.GREEN_SHULKER_BOX)
            || stack.getItem().equals(Items.RED_SHULKER_BOX)
            || stack.getItem().equals(Items.BLACK_SHULKER_BOX);
    }

    /** True if the shulker contains at least one stack matching the wanted stack (item + components). */
    public static boolean containsItem(ItemStack shulker, ItemStack wanted) {
        ContainerComponent box = shulker.get(DataComponentTypes.CONTAINER);
        if (box == null) return false;
        return box.stream().anyMatch(itemStack -> {
            if (itemStack.isEmpty()) return false;
            return ItemStack.areItemsAndComponentsEqual(itemStack, wanted);
        });
    }

    /** Read a 27-slot view of a shulker item’s contents. */
    public static DefaultedList<ItemStack> readContents(ItemStack shulker) {
        DefaultedList<ItemStack> out = DefaultedList.ofSize(SHULKER_SIZE, ItemStack.EMPTY);
        ContainerComponent box = shulker.get(DataComponentTypes.CONTAINER);
        if (box == null) return out;

        // The component exposes its internal stacks as an ordered list.
        List<ItemStack> stacks = box.stream().toList();
        int n = Math.min(stacks.size(), SHULKER_SIZE);
        for (int i = 0; i < n; i++) {
            ItemStack s = stacks.get(i);
            out.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return out;
    }

    /** Overwrite the shulker’s contents with exactly 27 slots from `contents`. */
    public static void writeContents(ItemStack shulker, DefaultedList<ItemStack> contents) {
        ContainerComponent updated = ContainerComponent.fromStacks(contents);
        shulker.set(DataComponentTypes.CONTAINER, updated);
    }

    /** Result of an extract with the last slot index we modified. */
    public record ExtractResult(int extracted, int lastTouchedIndex) {
        public static final ExtractResult NONE = new ExtractResult(0, -1);
    }

    /** Extract up to maxCount of target; returns amount and the last slot index touched (or -1). */
    public static ExtractResult extractWithIndex(ItemStack shulker, ItemStack target, int maxCount) {
        if (!isShulker(shulker) || maxCount <= 0) return ExtractResult.NONE;

        DefaultedList<ItemStack> slots = readContents(shulker);
        int remaining = maxCount;
        int lastIdx = -1;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, target)) {
                int take = Math.min(remaining, slot.getCount());
                slot.decrement(take);
                slots.set(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
                remaining -= take;
                lastIdx = i;
            }
        }

        int extracted = maxCount - remaining;
        if (extracted > 0) {
            writeContents(shulker, slots);
        }
        return new ExtractResult(extracted, lastIdx);
    }

    /** Try to place the given stack into the shulker at a preferred index; if unavailable, use first empty. */
    public static boolean swapIntoPreferIndex(ItemStack shulker, ItemStack hotbarStack, int preferredIndex) {
        if (!isShulker(shulker)) return false;
        if (hotbarStack.isEmpty()) return true; // nothing to store

        DefaultedList<ItemStack> slots = readContents(shulker);

        // If we have a preferred slot within range and it is empty, place it there.
        if (preferredIndex >= 0 && preferredIndex < slots.size() && slots.get(preferredIndex).isEmpty()) {
            slots.set(preferredIndex, hotbarStack.copy());
            writeContents(shulker, slots);
            return true;
        }

        // Otherwise use the first empty slot.
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, hotbarStack.copy());
                writeContents(shulker, slots);
                return true;
            }
        }

        // No empties: see if we can merge fully into an existing compatible stack.
        int toInsert = hotbarStack.getCount();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, hotbarStack)) {
                int room = Math.min(s.getMaxCount(), hotbarStack.getMaxCount()) - s.getCount();
                if (room > 0) {
                    int put = Math.min(room, toInsert);
                    s.increment(put);
                    toInsert -= put;
                    if (toInsert == 0) {
                        writeContents(shulker, slots);
                        return true;
                    }
                }
            }
        }

        // Could not fit everything; do not modify and signal failure so caller avoids dropping.
        return false;
    }

    /** Extract up to maxCount of target; returns extracted count. */
    public static int extract(ItemStack shulker, ItemStack target, int maxCount) {
        if (!isShulker(shulker) || maxCount <= 0) return 0;

        DefaultedList<ItemStack> slots = readContents(shulker);
        int remaining = maxCount;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, target)) {
                int take = Math.min(remaining, slot.getCount());
                slot.decrement(take);
                slots.set(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
                remaining -= take;
            }
        }

        if (remaining != maxCount) {
            writeContents(shulker, slots);
        }
        return maxCount - remaining;
    }

    /** Legacy signature: prefer swapping into the slot that still holds a matching stack; else empty; else merge. */
    public static boolean swapInto(ItemStack shulker, ItemStack hotbarStack, ItemStack extractedItem) {
        if (!isShulker(shulker)) return false;
        if (hotbarStack.isEmpty()) return true;

        DefaultedList<ItemStack> slots = readContents(shulker);

        // Try to overwrite a remaining matching stack (old behavior).
        for (int i = 0; i < slots.size(); i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, extractedItem)) {
                slots.set(i, hotbarStack.copy());
                writeContents(shulker, slots);
                return true;
            }
        }

        // If that failed because we fully drained the only stack, fall back to first empty or merge.
        // First empty:
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, hotbarStack.copy());
                writeContents(shulker, slots);
                return true;
            }
        }

        // Merge fully if possible:
        int toInsert = hotbarStack.getCount();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, hotbarStack)) {
                int room = Math.min(s.getMaxCount(), hotbarStack.getMaxCount()) - s.getCount();
                if (room > 0) {
                    int put = Math.min(room, toInsert);
                    s.increment(put);
                    toInsert -= put;
                    if (toInsert == 0) {
                        writeContents(shulker, slots);
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
