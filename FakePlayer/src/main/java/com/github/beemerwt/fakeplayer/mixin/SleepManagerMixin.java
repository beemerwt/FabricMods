package com.github.beemerwt.fakeplayer.mixin;

import com.github.beemerwt.fakeplayer.FakePlayerRegistry;
import net.minecraft.server.world.SleepManager;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SleepManager.class)
public class SleepManagerMixin {

    @Shadow
    private int total;

    @Inject(
        method = "getNightSkippingRequirement(I)I",
        at = @At("HEAD"),
        cancellable = true
    )
    public void fakeplayer$getNightSkippingRequirement(int percentage, CallbackInfoReturnable<Integer> cir) {
        var total = this.total - FakePlayerRegistry.list().size();
        cir.setReturnValue(Math.max(1, MathHelper.ceil((float)(total * percentage) / 100.0F)));
    }
}
