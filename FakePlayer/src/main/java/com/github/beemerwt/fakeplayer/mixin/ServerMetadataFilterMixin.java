package com.github.beemerwt.fakeplayer.mixin;

import com.github.beemerwt.fakeplayer.FakePlayerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerMetadata;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(MinecraftServer.class)
public abstract class ServerMetadataFilterMixin {
    @Shadow public abstract PlayerManager getPlayerManager();

    @Inject(method = "getServerMetadata", at = @At("RETURN"), cancellable = true)
    private void fakeplayer$filterServerMetadata(CallbackInfoReturnable<ServerMetadata> cir) {
        ServerMetadata original = cir.getReturnValue();
        PlayerManager pm = this.getPlayerManager();
        if (original == null || pm == null) return;

        List<ServerPlayerEntity> all = pm.getPlayerList();
        if (all.isEmpty()) return; // nothing to change

        // Build a UUID set for O(1) fake checks
        Set<java.util.UUID> fakeIds = FakePlayerRegistry.list().stream()
            .map(ServerPlayerEntity::getUuid)
            .collect(Collectors.toUnmodifiableSet());

        // Filter to real players only
        long realCount = all.stream().filter(p -> !fakeIds.contains(p.getUuid())).count();
        int max = pm.getMaxPlayerCount();

        // Replace the players section: set online to realCount, clear sample to avoid fake names
        ServerMetadata.Players filteredPlayers =
            new ServerMetadata.Players(max, (int) realCount, List.of());

        ServerMetadata filtered = new ServerMetadata(
            original.description(),
            Optional.of(filteredPlayers),
            original.version(),
            original.favicon(),
            original.secureChatEnforced()
        );

        cir.setReturnValue(filtered);
    }
}
