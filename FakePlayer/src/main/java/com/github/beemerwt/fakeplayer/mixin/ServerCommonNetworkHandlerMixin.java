package com.github.beemerwt.fakeplayer.mixin;

import com.github.beemerwt.fakeplayer.service.VisibilityService;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Shadow @Final protected MinecraftServer server;
    @Shadow @Final protected ClientConnection connection;

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$filterHiddenEntities(Packet<?> packet, CallbackInfo ci) {
        var self = (ServerCommonNetworkHandler) (Object) this;
        if (!(self instanceof ServerPlayNetworkHandler sph)) return;

        ServerPlayerEntity viewer = sph.player;
        if (viewer == null) return;

        Integer targetId = getTargetEntityId(packet);
        if (targetId != null && VisibilityService.isHidden(viewer, targetId)) {
            // swallow packet – viewer is not allowed to see this entity
            ci.cancel();
        }
    }

    @Unique
    private static Integer getTargetEntityId(Packet<?> p) {
        // Cover the common entity-scoped packets you care about:
        if (p instanceof EntitiesDestroyS2CPacket dp) return first(dp.getEntityIds());
        if (p instanceof EntitySpawnS2CPacket sp)   return sp.getEntityId();
        if (p instanceof EntityPositionS2CPacket ep) return ep.entityId();
        if (p instanceof EntityTrackerUpdateS2CPacket du) return du.id();
        if (p instanceof EntityEquipmentUpdateS2CPacket eq) return eq.getEntityId();
        if (p instanceof EntityAttributesS2CPacket at) return at.getEntityId();
        if (p instanceof EntityVelocityUpdateS2CPacket ve) return ve.getEntityId();
        if (p instanceof EntityPassengersSetS2CPacket pa) return pa.getEntityId();
        if (p instanceof EntityStatusEffectS2CPacket se) return se.getEntityId();
        if (p instanceof RemoveEntityStatusEffectS2CPacket rse) return rse.entityId();
        // add others you rely on (sound/particles are not entity-scoped)
        return null;
    }

    @Unique
    private static Integer first(IntList ids) { return ids.isEmpty() ? null : ids.getInt(0); }
}

