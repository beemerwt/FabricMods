package com.github.beemerwt.mcrpg.event;

import com.github.beemerwt.events.PlayerEvents;
import com.github.beemerwt.mcrpg.util.EventBus;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public class EventRegistry {
    public static void init() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player.isCreative()) return;

            if (!(world instanceof ServerWorld sw)) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;

            EventBus.emit(new BreakBlockEvent(sw, sp, state.getBlock(), pos, state, blockEntity, sp.getMainHandStack()));
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.isCreative()) return ActionResult.PASS;

            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            return EventBus.emit(new AttackBlockEvent(sp, sw, hand, pos, direction));
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.isCreative()) return ActionResult.PASS;

            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            return EventBus.emit(new UseItemEvent(sp, sw, hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isCreative()) return ActionResult.PASS;

            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            return EventBus.emit(new UseBlockEvent(sp, sw, hand, hitResult));
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, v) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return false;
            var result = EventBus.emit(new AllowDamageEvent(sp, source, v));
            return result == ActionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register(
            (entity, source, baseDamage, damageTaken, blocked) -> {
                if (!(entity instanceof ServerPlayerEntity sp)) return;
                EventBus.emit(new AfterDamageEvent(sp, source, baseDamage, damageTaken, blocked));
            }
        );

        PlayerEvents.BLOCK_PLACED.register((world, placer, pos, state, stack) -> {
            if (!(world instanceof ServerWorld sw)) return;
            if (!(placer instanceof ServerPlayerEntity sp)) return;

            EventBus.emit(new PlaceBlockEvent(sw, sp, pos, state, stack));
        });
    }
}
