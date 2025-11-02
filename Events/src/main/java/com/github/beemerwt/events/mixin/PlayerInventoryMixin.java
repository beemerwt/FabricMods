package com.github.beemerwt.events.mixin;

import com.github.beemerwt.events.PlayerEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow private int selectedSlot;
    @Shadow @Final public PlayerEntity player;
    @Unique private ItemStack events$prevMain = ItemStack.EMPTY;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"))
    private void events$beforeSelectSlot(int slot, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        events$prevMain = sp.getMainHandStack().copy();
    }

    @Inject(method = "setSelectedSlot", at = @At("TAIL"))
    private void events$afterSelectSlot(int slot, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(
                sp, EquipmentSlot.MAINHAND,
            events$prevMain,
                player.getMainHandStack()
        );
    }

    @Inject(method = "setSelectedStack", at = @At("TAIL"))
    private void events$onSetSelectedStack(ItemStack stack, CallbackInfoReturnable<ItemStack> ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(
                sp, EquipmentSlot.MAINHAND,
                ItemStack.EMPTY, // old unknown here; your listener can ignore
                player.getMainHandStack()
        );
    }

    // Capture old main-hand stack if the write targets the selected hotbar slot
    @Inject(method = "setStack", at = @At("HEAD"))
    private void events$captureOld(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot == selectedSlot) {
            events$prevMain = player.getMainHandStack().copy();
        }
    }

    // Apply after the write; if it hit the selected slot, refresh LimitBreak
    @Inject(method = "setStack", at = @At("TAIL"))
    private void events$afterSet(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != selectedSlot) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(sp, EquipmentSlot.MAINHAND,
            events$prevMain, player.getMainHandStack());
    }
}
