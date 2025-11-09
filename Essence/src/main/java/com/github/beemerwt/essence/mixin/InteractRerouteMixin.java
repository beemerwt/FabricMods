// Mixin into ServerPlayNetworkHandler to convert "use item" into a block use when a highlight is active.
package com.github.beemerwt.essence.mixin;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.util.HighlightEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class InteractRerouteMixin {
    @Shadow public ServerPlayerEntity player;

    /**
     * Intercept "use item" packets to reroute to block interaction if a highlight is active.
     */
    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"))
    private void essence$onUseEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        Essence.getLogger().info("onPlayerInteractEntity triggered");
        if (!hasActiveHighlight(player)) return;
        if (player.isSneaking()) return;

        double reach = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        BlockHitResult bhr = raycastBlock(player, reach);
        if (bhr == null || bhr.getType() != HitResult.Type.BLOCK)
            return;

        ActionResult res = player.interactionManager.interactBlock(
            player, player.getEntityWorld(), player.getMainHandStack(), Hand.MAIN_HAND, bhr
        );

        if (res.isAccepted())
            sendSwing(player);
    }

    @Unique
    private static void sendSwing(ServerPlayerEntity player) {
        EntityAnimationS2CPacket pkt = new EntityAnimationS2CPacket(player, 0);
        ServerWorld world = player.getEntityWorld();
        world.getChunkManager().sendToNearbyPlayers(player, pkt); // others see it
        player.networkHandler.sendPacket(pkt);                    // player sees it
    }

    @Unique
    private static BlockHitResult raycastBlock(ServerPlayerEntity p, double reach) {
        Vec3d eye = p.getCameraPosVec(1.0f);
        Vec3d end = eye.add(p.getRotationVec(1.0f).multiply(reach));
        RaycastContext ctx = new RaycastContext(
            eye, end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            p
        );
        return p.getEntityWorld().raycast(ctx);
    }

    @Unique
    private static boolean hasActiveHighlight(ServerPlayerEntity p) {
        return HighlightEntity.hasHighlight(p.getUuid());
    }

    // Optional: check packet target against your phantom UUIDs if you want stricter gating.
    @Unique
    @SuppressWarnings("unused")
    private static boolean isOurHighlightTarget(PlayerInteractEntityC2SPacket packet) {
        return true;
    }
}
