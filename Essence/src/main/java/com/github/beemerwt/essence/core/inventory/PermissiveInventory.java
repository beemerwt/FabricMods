package com.github.beemerwt.essence.core.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public record PermissiveInventory(Inventory delegate) implements Inventory {
    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return delegate.getStack(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return delegate.removeStack(slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return delegate.removeStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        delegate.setStack(slot, stack);
    }

    @Override
    public void markDirty() {
        delegate.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
