package com.github.beemerwt.essence.events;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.util.Locations;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class PlayerEvents {
    private static final TagKey<Block> BEDS = TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:beds"));

    public static void register() {
        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if (!(playerEntity instanceof ServerPlayerEntity sp))
                return ActionResult.PASS;

            var pos = blockHitResult.getBlockPos();
            var block = world.getBlockState(pos).getBlock();
            if (Registries.BLOCK.getEntry(block).isIn(BEDS)) {
                var loc = Locations.capture(sp).withPos(pos);
                Essence.getLocationStore().setSingle(sp.getUuid(), LocationType.SPAWN, loc);
                sp.sendMessage(Text.literal("Respawn point set."));
            }

            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, playerEntity, blockPos, blockState, blockEntity) -> {

        });
    }
}
