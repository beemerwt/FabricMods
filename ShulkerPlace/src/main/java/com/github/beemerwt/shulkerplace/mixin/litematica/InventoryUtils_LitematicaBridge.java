package com.github.beemerwt.shulkerplace.mixin.litematica;

import com.github.beemerwt.shulkerplace.client.PostPickRunner;
import com.github.beemerwt.shulkerplace.core.ShulkerIO;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.InventoryUtils")
public class InventoryUtils_LitematicaBridge {

    // ThreadLocal to pass the original "wanted" stack from HEAD to our redirect
    private static final ThreadLocal<ItemStack> SHULKERPLACE$WANTED = new ThreadLocal<>();

    // Signature: schematicWorldPickBlock(ItemStack stack, BlockPos pos, World schematicWorld, MinecraftClient mc)
    @Inject(
        method = "schematicWorldPickBlock(Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/World;Lnet/minecraft/client/MinecraftClient;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void shulkerplace$captureWanted(
        ItemStack stack, BlockPos pos, World schematicWorld, MinecraftClient mc, CallbackInfo ci
    ) {
        LogUtils.getLogger().info("ShulkerPlace: captured wanted stack {}", stack);
        // keep a copy so mutations will not affect our comparison
        SHULKERPLACE$WANTED.set(stack == null ? ItemStack.EMPTY : stack.copy());
    }

    /**
     * There are two calls to setPickedItemToHand(...):
     *  - ordinal 0: normal pick (direct item in inventory)
     *  - ordinal 1: shulker path (boxStack) when PICK_BLOCK_SHULKERS is enabled
     * We replace only ordinal 1.
     */
    @Redirect(
        method = "schematicWorldPickBlock(Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/World;Lnet/minecraft/client/MinecraftClient;)V",
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lfi/dy/masa/litematica/util/InventoryUtils;findSlotWithBoxWithItem(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/item/ItemStack;Z)I"
            ),
            to = @At("TAIL")
        ),
        at = @At(
            value = "INVOKE",
            target = "Lfi/dy/masa/litematica/util/InventoryUtils;setPickedItemToHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/MinecraftClient;)V"
        ),
        require = 0
    )
    private static void shulkerplace$redirectShulkerPick(ItemStack boxStack, MinecraftClient mc) {
        LogUtils.getLogger().info("ShulkerPlace: redirecting shulker pick for box stack {}", boxStack);

        try {
            var wanted = SHULKERPLACE$WANTED.get();
            var player = mc.player;
            if (player == null || wanted == null || wanted.isEmpty()) {
                // fallback to Litematica’s original behavior if we cannot proceed
                callOriginalSetPickedItemToHand(boxStack, mc);
                return;
            }

            // Use the currently selected hotbar slot as the target
            int preferredSlot = player.getInventory().getSelectedSlot();

            // Ask our client helper to request the server to pull the item out of shulkers
            boolean ok = PostPickRunner.tryPickFromAnyShulker(mc, wanted, preferredSlot);

            if (!ok) {
                // no matching shulker found or client refused; preserve Litematica behavior
                callOriginalSetPickedItemToHand(boxStack, mc);
            }
            // If ok, do nothing else. We have sent our payload and prevented Litematica from swapping in the shulker.
        } finally {
            // clear after use
            SHULKERPLACE$WANTED.remove();
        }
    }

    // Small indirection to call the original method via the same owner, so the redirect can still fall back cleanly.
    @Unique
    private static void callOriginalSetPickedItemToHand(ItemStack stack, MinecraftClient mc) {
        // This method body will be replaced at runtime by the redirect target.
        // The call will be re-invoked as if our redirect did not exist.
        // Mixin requires a stub here, but it never actually runs.
        LogUtils.getLogger().info("ShulkerPlace: calling original setPickedItemToHand for stack {}", stack);
        throw new AssertionError("Untransformed callOriginalSetPickedItemToHand");
    }
}
