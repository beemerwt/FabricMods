package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.PlayerEvents;
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
    @Shadow
    public int selectedSlot;

    @Shadow @Final
    public PlayerEntity player;

    @Unique
    private ItemStack mcrpg$prevMain = ItemStack.EMPTY;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"))
    private void mcrpg$beforeSelectSlot(int slot, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        mcrpg$prevMain = sp.getMainHandStack().copy();
    }

    @Inject(method = "setSelectedSlot", at = @At("TAIL"))
    private void mcrpg$afterSelectSlot(int slot, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        McRPG.getLogger().debug("Player {} changed selected hotbar slot to {}",
                sp.getName().getString(), slot);

        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(
                sp, EquipmentSlot.MAINHAND,
                mcrpg$prevMain,
                player.getMainHandStack()
        );
    }

    @Inject(method = "setSelectedStack", at = @At("TAIL"))
    private void mcrpg$onSetSelectedStack(ItemStack stack, CallbackInfoReturnable<ItemStack> ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        McRPG.getLogger().debug("Player {} set selected hotbar stack to {}",
                sp.getName().getString(), stack);

        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(
                sp, EquipmentSlot.MAINHAND,
                ItemStack.EMPTY, // old unknown here; your listener can ignore
                player.getMainHandStack()
        );
    }

    // Capture old main-hand stack if the write targets the selected hotbar slot
    @Inject(method = "setStack", at = @At("HEAD"))
    private void mcrpg$captureOld(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot == selectedSlot) {
            mcrpg$prevMain = player.getMainHandStack().copy();
        }
    }

    // Apply after the write; if it hit the selected slot, refresh LimitBreak
    @Inject(method = "setStack", at = @At("TAIL"))
    private void mcrpg$afterSet(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != selectedSlot) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        PlayerEvents.CHANGE_SLOT.invoker().onChangeSlot(sp, EquipmentSlot.MAINHAND,
                mcrpg$prevMain, player.getMainHandStack());
    }
}
