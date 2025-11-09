package com.github.beemerwt.shulkerplace.core;

import com.github.beemerwt.shulkerplace.net.PickFromShulkerPayload;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class PickLogic {
    private record ExtractTxn(int invSlot, int lastTouchedIndex, int count) {}

    public static void handlePickLogic(ServerPlayerEntity player, PickFromShulkerPayload req) {
        if (player.isSpectator() || player.isCreative()) return;

        final int hotbarIdx = req.selectedHotbarSlot();
        final Item requested = Registries.ITEM.get(req.itemId());
        final ItemStack requestedOne = requested.getDefaultStack();
        if (requestedOne.isEmpty()) return;

        // 1) Scan inventory for shulkers and extract up to a full stack, tracking where we pulled from.
        int remaining = requestedOne.getMaxCount();
        ItemStack grabbed = ItemStack.EMPTY;
        List<ExtractTxn> txns = new ArrayList<>();

        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack slot = player.getInventory().getStack(i);
            if (!ShulkerIO.isShulker(slot)) continue;

            var res = ShulkerIO.extractWithIndex(slot, requestedOne, remaining);
            if (res.extracted() <= 0) continue;

            if (grabbed.isEmpty()) grabbed = req.asStack(res.extracted());
            else grabbed.increment(res.extracted());

            txns.add(new ExtractTxn(i, res.lastTouchedIndex(), res.extracted()));
            remaining -= res.extracted();
        }

        if (grabbed.isEmpty()) return; // nothing found

        // 2) If the selected slot is empty, place and finish.
        ItemStack currentHotbar = InventoryOps.getSelected(player, hotbarIdx);
        if (currentHotbar.isEmpty()) {
            InventoryOps.setSelected(player, hotbarIdx, grabbed);
            player.getInventory().markDirty();
            return;
        }

        // 3) If there is an empty main slot, move the hotbar stack aside, then place the grabbed stack.
        int emptyMain = InventoryOps.firstEmptyMainSlot(player);
        if (emptyMain != -1) {
            InventoryOps.setSlot(player, emptyMain, currentHotbar.copy());
            InventoryOps.setSelected(player, hotbarIdx, grabbed);
            player.getInventory().markDirty();
            return;
        }

        // 4) Inventory full: we must swap the displaced hotbar stack back into a shulker.
        // Try the last shulker we touched first (best chance the preferred index is empty).
        boolean storedDisplaced = false;
        if (!txns.isEmpty()) {
            ExtractTxn last = txns.getLast();
            ItemStack shulker = player.getInventory().getStack(last.invSlot());
            storedDisplaced = ShulkerIO.swapIntoPreferIndex(shulker, currentHotbar, last.lastTouchedIndex());
        }

        // If that fails, try every shulker we interacted with (preferred index unknown or taken).
        if (!storedDisplaced) {
            for (int k = txns.size() - 1; k >= 0 && !storedDisplaced; k--) {
                ExtractTxn t = txns.get(k);
                ItemStack shulker = player.getInventory().getStack(t.invSlot());
                storedDisplaced = ShulkerIO.swapIntoPreferIndex(shulker, currentHotbar, -1);
            }
        }

        // As a final attempt, try any other shulker in the player inventory.
        if (!storedDisplaced) {
            for (int i = 0; i < player.getInventory().size() && !storedDisplaced; i++) {
                ItemStack slot = player.getInventory().getStack(i);
                if (!ShulkerIO.isShulker(slot)) continue;
                storedDisplaced = ShulkerIO.swapIntoPreferIndex(slot, currentHotbar, -1);
            }
        }

        if (storedDisplaced) {
            // Safe to place the grabbed stack now that the displaced one has a home.
            InventoryOps.setSelected(player, hotbarIdx, grabbed);
            player.getInventory().markDirty();
            return;
        }

        // 5) Could not store the displaced item anywhere. Roll back the extract so we do not drop.
        // Try to put the grabbed items back into the same shulkers we pulled from.
        int toRollback = grabbed.getCount();
        for (int k = txns.size() - 1; k >= 0 && toRollback > 0; k--) {
            ExtractTxn t = txns.get(k);
            ItemStack shulker = player.getInventory().getStack(t.invSlot());
            // Prefer the exact slot we emptied, then anywhere.
            if (ShulkerIO.swapIntoPreferIndex(shulker, req.asStack(Math.min(toRollback, grabbed.getMaxCount())), t.lastTouchedIndex())) {
                toRollback -= Math.min(toRollback, grabbed.getMaxCount());
            }
        }
        // If some remainder still exists, try any shulker to avoid loss.
        while (toRollback > 0) {
            boolean placedSome = false;
            for (int i = 0; i < player.getInventory().size() && toRollback > 0; i++) {
                ItemStack slot = player.getInventory().getStack(i);
                if (!ShulkerIO.isShulker(slot)) continue;
                int chunk = Math.min(toRollback, grabbed.getMaxCount());
                if (ShulkerIO.swapIntoPreferIndex(slot, req.asStack(chunk), -1)) {
                    toRollback -= chunk;
                    placedSome = true;
                }
            }
            if (!placedSome) break; // nowhere to put the rest
        }

        // 6) If rollback fully succeeded, do nothing and exit. If not, drop only the grabbed remainder.
        if (toRollback > 0) {
            ItemStack remainder = req.asStack(toRollback);
            player.dropItem(remainder, true, false);
        }

        // The player’s original hotbar stack was never removed, so no accidental loss.
        player.getInventory().markDirty();
    }
}
