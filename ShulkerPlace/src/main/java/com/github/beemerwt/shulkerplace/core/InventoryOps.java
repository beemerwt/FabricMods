package com.github.beemerwt.shulkerplace.core;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class InventoryOps {
    private InventoryOps() {}

    /** Find first empty main-inventory slot (not hotbar). Returns -1 if none. */
    public static int firstEmptyMainSlot(PlayerEntity player) {
        // Main inventory indices are 9..35 in PlayerInventory
        for (int i = 9; i <= 35; i++) {
            if (player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Place stack into exact slot index, marking dirty. */
    public static void setSlot(ServerPlayerEntity player, int slot, ItemStack stack) {
        player.getInventory().setStack(slot, stack);
        player.playerScreenHandler.sendContentUpdates();
        player.currentScreenHandler.sendContentUpdates();
    }

    /** Convenience: get and set hotbar selected slot. */
    public static ItemStack getSelected(ServerPlayerEntity p, int hotbarIndex) {
        return p.getInventory().getStack(hotbarSlot(hotbarIndex));
    }

    public static void setSelected(ServerPlayerEntity p, int hotbarIndex, ItemStack stack) {
        setSlot(p, hotbarSlot(hotbarIndex), stack);
    }

    public static int hotbarSlot(int hotbarIndex) {
        // PlayerInventory hotbar is 0..8
        return Math.max(0, Math.min(8, hotbarIndex));
    }
}
