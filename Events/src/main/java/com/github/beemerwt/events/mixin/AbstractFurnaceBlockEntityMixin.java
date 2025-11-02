package com.github.beemerwt.events.mixin;

import com.github.beemerwt.events.FurnaceEvents;
import com.github.beemerwt.events.duck.FuelTime;
import com.github.beemerwt.events.proxy.AbstractFurnaceBlockEntityProxy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Inject where one item is crafted/smelted and moved from input to output.
 * The exact method name can vary; in 1.21.x yarn there is usually a
 * method that performs the "doSmelt/quickRecipeTransfer" step inside tick.
 * If needed, adjust the target to the line right after it decrements the input and increments output.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements FuelTime {
    @Shadow
    int litTimeRemaining;

    /**
     * We target the INVOKE of craftRecipe(...), and run AFTER it returns. If that call returned true,
     * vanilla immediately calls setLastRecipe(recipeEntry) — we’re positioned right after that site.
     */
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;craftRecipe(" +
                "Lnet/minecraft/registry/DynamicRegistryManager;" +
                "Lnet/minecraft/recipe/RecipeEntry;" +
                "Lnet/minecraft/recipe/input/SingleStackRecipeInput;" +
                "Lnet/minecraft/util/collection/DefaultedList;" +
                "I)Z"
        )
    )
    private static boolean events$wrapCraftRecipe(
        // ---- The actual arguments that tick passes into craftRecipe(...)
        DynamicRegistryManager drm,
        RecipeEntry<? extends AbstractCookingRecipe> recipe,
        SingleStackRecipeInput input,
        DefaultedList<ItemStack> inventory,
        int maxCount,
        // ---- The original call
        Operation<Boolean> original,

        // ---- Original tick(...) params so we have full BE context
        @Local(argsOnly = true) ServerWorld world,
        @Local(argsOnly = true) BlockPos pos,
        @Local(argsOnly = true) BlockState state,
        @Local(argsOnly = true) AbstractFurnaceBlockEntity be
    ) {
        // Snapshot the *true* input and computed output BEFORE the original mutates anything
        final ItemStack inputStack  = input.item().copy();

        // Call vanilla
        boolean crafted = original.call(drm, recipe, input, inventory, maxCount);

        // Fire only if a craft actually happened
        if (crafted && recipe != null) {
            var outputStack = be.getStack(2);
            var proxy = AbstractFurnaceBlockEntityProxy.obtain(state, pos, world, be);
            FurnaceEvents.ITEM_SMELTED.invoker().onSmeltedItem(proxy, inputStack, outputStack);
            AbstractFurnaceBlockEntityProxy.release();
        }

        return crafted;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrement(I)V"
            ),
            locals = LocalCapture.NO_CAPTURE
    )
    private static void events$beforeFuelConsumed(
            ServerWorld world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be,
            CallbackInfo ci
    ) {
        try {
            var proxy = AbstractFurnaceBlockEntityProxy.obtain(state, pos, world, be);
            ItemStack fuelStack = be.getStack(1); // inventory[1]
            FurnaceEvents.FUEL_CONSUMED.invoker().onFuelConsumed(proxy, fuelStack);
        } finally {
            AbstractFurnaceBlockEntityProxy.release();
        }
    }

    @Override
    public void addFuelTime(int extra) {
        if (extra <= 0) return;
        long sum = (long) this.litTimeRemaining + (long) extra;
        if (sum < 0) sum = 0;                 // just in case of overflow negative
        if (sum > Integer.MAX_VALUE) sum = Integer.MAX_VALUE;
        this.litTimeRemaining = (int) sum;
    }

    @Override
    public int getFuelTime() {
        return litTimeRemaining;
    }
}

