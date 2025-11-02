package com.github.beemerwt.events.mixin;

import com.github.beemerwt.events.PlayerEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockOnPlacedMixin {
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void onBlockPlaced(
        World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack, CallbackInfo ci
    ) {
        PlayerEvents.BLOCK_PLACED.invoker().onBlockPlace(world, placer, pos, state, stack);
    }
}
