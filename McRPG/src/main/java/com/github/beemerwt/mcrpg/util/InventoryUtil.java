package com.github.beemerwt.mcrpg.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Math;

public class InventoryUtil {
    public static int countInInventory(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) total += s.getCount();
        }
        return total;
    }

    public static void removeFromInventory(ServerPlayerEntity player, Item item, int toRemove) {
        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int take = Math.min(s.getCount(), toRemove);
            s.decrement(take);
            toRemove -= take;
        }
    }
}
