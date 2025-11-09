package com.github.beemerwt.shulkerplace.client;

import com.github.beemerwt.shulkerplace.core.ShulkerIO;
import com.github.beemerwt.shulkerplace.net.PickFromShulkerPayload;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class PostPickRunner {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private PostPickRunner() {}

    /**
     * Called from the Litematica redirect:
     * Attempts to satisfy "wanted" by pulling it from any shulker in the player's inventory.
     * Returns true if a request was sent to the server (Litematica should NOT swap a shulker in-hand).
     */
    public static boolean tryPickFromAnyShulker(MinecraftClient mc, ItemStack wanted, int preferredHotbarSlot) {
        if (mc == null || mc.player == null || wanted == null || wanted.isEmpty()) return false;

        PlayerInventory inv = mc.player.getInventory();

        // Creative path should be handled by Litematica's own branch; avoid double-work.
        if (mc.player.isCreative()) return false;

        int shulkerSlot = findFirstMatchingShulker(inv, wanted);
        if (shulkerSlot < 0) {
            LogUtils.getLogger().debug("ShulkerPlace: no shulker contains {}", Registries.ITEM.getId(wanted.getItem()));
            return false;
        }

        // Your payload currently only needs hotbar slot + item id; the server searches shulkers.
        ClientPlayNetworking.send(new PickFromShulkerPayload(
            preferredHotbarSlot,
            Registries.ITEM.getId(wanted.getItem())
        ));

        LogUtils.getLogger().info("ShulkerPlace: requested pull of {} into hotbar {}",
            Registries.ITEM.getId(wanted.getItem()), preferredHotbarSlot);
        return true;
    }

    /** Returns the first inventory slot index holding a shulker that contains the wanted item, or -1. */
    private static int findFirstMatchingShulker(PlayerInventory inv, ItemStack wanted) {
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStack(slot);
            if (!ShulkerIO.isShulker(stack)) continue;
            if (ShulkerIO.containsItem(stack, wanted)) return slot;
        }
        return -1;
    }

    public static void tryShulkerPickAfterVanilla() {
        if (MC.player == null || MC.world == null) {
            LogUtils.getLogger().info("PostPickRunner: no player or world; aborting shulker pick");
            return;
        }

        ItemStack target = resolvePickedStack();
        if (target.isEmpty()) {
            LogUtils.getLogger().info("PostPickRunner: could not resolve picked stack; aborting shulker pick");
            return;
        }

        // If vanilla succeeded, inventory already has the item. Bail out.
        if (playerHasItem(target)) {
            LogUtils.getLogger().info("PostPickRunner: player already has item after vanilla pick; aborting shulker pick");
            return;
        }

        int hotbarIdx = MC.player.getInventory().getSelectedSlot();
        ClientPlayNetworking.send(new PickFromShulkerPayload(hotbarIdx, Registries.ITEM.getId(target.getItem())));
        LogUtils.getLogger().info("PostPickRunner: sent shulker pick request for item {}",
            Registries.ITEM.getId(target.getItem()));
    }

    private static boolean playerHasItem(ItemStack probe) {
        if (MC.player == null) return false;

        var inv = MC.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var s = inv.getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, probe))
                return true;
        }

        return false;
    }

    /** Mirror vanilla target resolution so we ask the server for the right stack. */
    private static ItemStack resolvePickedStack() {
        if (MC.world == null) return ItemStack.EMPTY;
        if (MC.player == null) return ItemStack.EMPTY;

        HitResult hr = MC.crosshairTarget;
        if (hr == null) return ItemStack.EMPTY;

        return switch (hr.getType()) {
            case BLOCK -> {
                BlockHitResult bhr = (BlockHitResult) hr;
                BlockPos pos = bhr.getBlockPos();
                var state = MC.world.getBlockState(pos);
                var stack = state.getPickStack(MC.world, pos, true);
                yield stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
            }
            case ENTITY -> {
                Entity e = ((EntityHitResult) hr).getEntity();
                var stack = e.getPickBlockStack();
                yield stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
            }
            default -> {
                // Fallback: use the held item type so the feature still works when pointing at air
                ItemStack ref = MC.player.getMainHandStack();
                if (ref.isEmpty()) ref = MC.player.getOffHandStack();
                yield ref.isEmpty() ? ItemStack.EMPTY : ref.copyWithCount(1);
            }
        };
    }
}

