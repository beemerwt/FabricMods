package com.github.beemerwt.essence.mixin;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.util.Locations;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep the reserved home "bed" synced with the player’s respawn point.
 * This runs whenever the server sets a player's spawn (bed, anchor, or command).
 */
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerRespawnMixin {
    @Inject(method="setSpawnPoint", at=@At("TAIL"))
    private void essence$onSetSpawn(
            @Nullable ServerPlayerEntity.Respawn respawn, boolean sendMessage, CallbackInfo ci
    ) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (respawn == null || respawn.respawnData() == null || respawn.respawnData().getPos() == null) {
            self.sendMessage(Text.literal("Your respawn point has been deleted!"), false);
            Essence.getLocationStore().deleteSingle(self.getUuid(), LocationType.SPAWN);
            return;
        }

        var loc = Locations.fromSpawnPoint(respawn.respawnData(), new Vec3d(0, 1, 0));
        Essence.getLocationStore().setSingle(self.getUuid(), LocationType.SPAWN, loc);
    }
}
