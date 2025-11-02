package com.github.beemerwt.fakeplayer.mixin;

import com.github.beemerwt.fakeplayer.FakePlayerRegistry;
import net.minecraft.server.command.ListCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Mixin(ListCommand.class)
public class ListCommandMixin {

    @Inject(
        method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/function/Function;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void fakeplayer$execute(
        ServerCommandSource source,
        Function<ServerPlayerEntity, Text> nameProvider,
        CallbackInfoReturnable<Integer> cir
    ) {
        var server = source.getServer();
        if (server == null) return;

        var pm = server.getPlayerManager();
        if (pm == null) return;

        List<ServerPlayerEntity> all = pm.getPlayerList();
        if (all.isEmpty()) return;

        // Build a fast lookup for fake players (prefer a helper like FakePlayerRegistry.isFake(UUID))
        Set<UUID> fakeIds = FakePlayerRegistry.list().stream()
            .map(ServerPlayerEntity::getUuid)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        // Filter to real players
        List<ServerPlayerEntity> realPlayers = all.stream()
            .filter(p -> !fakeIds.contains(p.getUuid()))
            .toList();

        // If nothing changed, let vanilla handle
        if (realPlayers.size() == all.size()) return;

        // Join names and send our own output
        Text joined = Texts.join(realPlayers, nameProvider);
        int realCount = realPlayers.size();
        int max = pm.getMaxPlayerCount();

        source.sendFeedback(
            () -> Text.translatable("commands.list.players", realCount, max, joined),
            false
        );

        // Return value should be the number of shown players, and cancel vanilla execution
        cir.setReturnValue(realCount);
        cir.cancel();
    }
}
