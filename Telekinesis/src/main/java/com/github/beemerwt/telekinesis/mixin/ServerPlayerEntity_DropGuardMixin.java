package com.github.beemerwt.telekinesis.mixin;

import com.github.beemerwt.telekinesis.TeleContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntity_DropGuardMixin {

    @Inject(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD")
    )
    private void tk$beginDropGuard(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                   CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
        TeleContext.beginPlayerDrop(self.getUuid());
    }

    @Inject(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("TAIL")
    )
    private void tk$endDropGuard(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                 CallbackInfoReturnable<ItemEntity> cir) {
        TeleContext.endPlayerDrop();
    }
}

