package com.github.beemerwt.fakeplayer.mixin;

import com.github.beemerwt.fakeplayer.FakePlayerRegistry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerQueryNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public class HideFakePlayersMixin {
    @Inject(
        method = "getCurrentPlayerCount",
        at = @At("HEAD"),
        cancellable = true
    )
    public void fakeplayer$getCurrentPlayerCount(CallbackInfoReturnable<Integer> cir)
    {


        var thisPlayerManager = (PlayerManager)(Object)this;
        var totalSize = thisPlayerManager.getPlayerList().size();
        var fakePlayers = FakePlayerRegistry.list().size();
        cir.setReturnValue(totalSize - fakePlayers);
    }

}
