package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.capability.NoclipCapability;
import com.github.beemerwt.essence.core.duck.INoclip;
import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public class NoclipCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(commandLiteral("essence", "noclip")
            .requires(Perms.NOCLIP)
            .executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                boolean on = !Essence.getPlayerStore().get(p).noClip();

                try {
                    ((INoclip) p).essence$setNoclip(on);
                    Essence.getPlayerStore().setNoClip(p, on);
                } catch (Exception e) {
                    Essence.getLogger().error("Failed to toggle noclip for {}", p.getName().getString(), e);
                    p.sendMessage(Text.literal("An error occurred while toggling noclip."), false);
                    return 0;
                }

                // QoL movement - only disable flying for non-creative players
                var ab = p.getAbilities();
                if (on) { ab.allowFlying = true; ab.flying = true; }
                else if (!p.isCreative()) { ab.flying = false; ab.allowFlying = false; }
                p.fallDistance = 0; p.sendAbilitiesUpdate();

                // Nudge out when turning OFF if embedded
                if (!on && p.isInsideWall()) p.requestTeleport(p.getX(), Math.ceil(p.getY()), p.getZ());

                // Smooth client gets synced; vanilla safely ignored
                NoclipCapability.sendSyncIfSupported(p, on);

                Essence.getLogger().info("Set noclip for {} to {}", p.getName().getString(), on);
                p.sendMessage(Text.literal(on ? "Noclip enabled." : "Noclip disabled."), false);
                return 1;
            })
        );
    }
}
