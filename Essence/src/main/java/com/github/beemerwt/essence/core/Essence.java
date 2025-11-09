package com.github.beemerwt.essence.core;

import com.github.beemerwt.essence.core.async.AsyncChunkBroker;
import com.github.beemerwt.essence.core.capability.NoclipCapability;
import com.github.beemerwt.essence.core.command.*;
import com.github.beemerwt.essence.core.data.*;
import com.github.beemerwt.essence.core.payload.NoclipCapC2SPayload;
import com.github.beemerwt.essence.core.payload.NoclipSyncS2CPayload;
import com.github.beemerwt.essence.core.permission.Permissions;
import com.github.beemerwt.essence.core.util.HighlightEntity;
import com.github.beemerwt.essence.core.util.Locations;
import com.github.beemerwt.essence.core.util.Teleporter;
import com.github.beemerwt.util.FabricLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

// TODO: Commands mods
// TODO: Count command to count types of mobs
// TODO: World command to change worlds at the exact coordinate you are standing (helps with portal placement)

public final class Essence implements ModInitializer {
    public static final String MOD_ID = "essence";

    private static final EssenceConfig CONFIG = new EssenceConfig();
    public static EssenceConfig getConfig() { return CONFIG; }

    private static PlayerStore playerStore;
    public static PlayerStore getPlayerStore() { return playerStore; }

    private static LocationStore locationStore;
    public static LocationStore getLocationStore() { return locationStore; }

    private static SuspensionStore suspensionStore;
    public static SuspensionStore getSuspensionStore() { return suspensionStore; }

    private static final FabricLogger LOG = new FabricLogger("Essence");
    public static FabricLogger getLogger() { return LOG; }

    private static MinecraftServer server;
    public static @NotNull MinecraftServer getServer() { return server; }

    @Override
    public void onInitialize() {
        LOG.info("Essence initializing...");
        // PlayerEvents.register();
        HighlightEntity.register();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, regAccess, env) -> {
            HomeCommands.register(dispatcher);
            SpawnCommands.register(dispatcher);
            BackCommand.register(dispatcher);
            WarpCommands.register(dispatcher);
            TpaCommands.register(dispatcher);
            EnchantCommand.register(dispatcher);
            WorldCommand.register(dispatcher);

            // Management
            JailCommands.register(dispatcher);
            BanCommands.register(dispatcher);
            MuteCommands.register(dispatcher);
            KickCommand.register(dispatcher);

            // Utility
            DirectionCommands.register(dispatcher);
            FlyCommand.register(dispatcher);
            TeleportCommand.register(dispatcher);
            SummonCommand.register(dispatcher, regAccess);

            FindItemCommand.register(dispatcher, regAccess);
            InvSeeCommand.register(dispatcher);

            CountCommand.register(dispatcher);

            NoclipCommand.register(dispatcher);
            HighlightCommand.register(dispatcher);
        });

        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            var uuid = handler.getPlayer().getGameProfile().id();
            var ban = getSuspensionStore().getActiveBan(uuid);
            if (ban.isPresent()) {
                var b = ban.get();
                if (b.expiresAt() != null && b.expiresAt().isBefore(Instant.now())) {
                    getSuspensionStore().unban(uuid); // auto-expire
                    return;
                }

                String msg = (b.expiresAt() == null)
                    ? "You are banned.\nReason: " + b.reason()
                    : "You are banned until " + b.expiresAt() + ".\nReason: " + b.reason();
                handler.disconnect(Text.literal(msg));
            }
        });

            // Load storage on server start; save on stop
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Essence.server = server;
            CONFIG.load();
            Permissions.init(); // Load permissions after player data is loaded
            playerStore = new PlayerStore();
            locationStore = new LocationStore();
            suspensionStore = new SuspensionStore();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Essence.server = null;
            try {
                // Flush committed pages back into essence.db and truncate the WAL
                try (var st = Database.get().conn().createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            } catch (Exception e) {
                Essence.getLogger().warning("wal_checkpoint(TRUNCATE) failed", e);
            } finally {
                try {
                    Database.get().close();
                } catch (Exception e) {
                    Essence.getLogger().warning("Database close failed", e);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AsyncChunkBroker.reset());

        // Load per-player data on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            playerStore.get(p); // Load player data

            registerNoclip(handler);
            registerRespawn(handler, server);
        });

        // Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            NoclipCapability.setSupported(p, false);
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (suspensionStore.isMuted(sender.getUuid())) {
                var m = suspensionStore.getActiveMute(sender.getUuid()).orElse(null);
                String until = (m != null && m.expiresAt() != null) ? " until " + m.expiresAt() : " permanently";
                sender.sendMessage(Text.literal("You are muted" + until + "."
                        + (m != null ? " Reason: " + m.reason() : "")), false);
                return false; // block the chat message
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var now = Instant.now();
            AsyncChunkBroker.pump();

            for (var player : server.getPlayerManager().getPlayerList()) {
                var jail = suspensionStore.getActiveJail(player.getUuid());
                if (jail.isEmpty()) continue;
                var jr = jail.get();
                if (jr.expiresAt().isBefore(now)) {
                    suspensionStore.unjail(player.getUuid());
                    continue;
                }

                suspensionStore.getJail(jr.jailName()).ifPresent(loc -> {
                    // if player strayed, snap back (you can add a threshold)
                    if (!player.getEntityWorld().getRegistryKey().equals(loc.worldKey())
                            || player.squaredDistanceTo(loc.x(), loc.y(), loc.z()) > 4.0) {
                        var world = Locations.resolveWorld(loc);
                        if (world != null)
                            Teleporter.teleportTo(player, loc);
                    }
                });
            }
        });

        PayloadTypeRegistry.playS2C().register(NoclipSyncS2CPayload.ID, NoclipSyncS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NoclipCapC2SPayload.ID, NoclipCapC2SPayload.CODEC);

        // client capability ping
        ServerPlayNetworking.registerGlobalReceiver(NoclipCapC2SPayload.ID, (payload, context) -> {
            var player = context.player();
            context.server().execute(() -> NoclipCapability.setSupported(player, true));
            // now that we know they support it, push current state
            context.server().execute(() -> {
                boolean on = playerStore.get(player.getUuid()).noClip();
                if (on) ServerPlayNetworking.send(player, new NoclipSyncS2CPayload(true));
            });
        });

        // Ensure spawn data exists
        LOG.info("Essence initialized.");
    }

    private void registerRespawn(ServerPlayNetworkHandler handler, MinecraftServer server) {
        ServerPlayerEntity p = handler.player;
        var respawn = p.getRespawn();
        if (respawn == null) return; // no bed or respawn anchor

        var data = respawn.respawnData();
        if (data == null) return; // no respawn data

        var world = server.getWorld(data.getDimension());
        if (world == null) return; // invalid dimension

        if (world.getSpawnPoint().equals(data)) return; // at world spawn, ignore

        var bedLoc = Locations.fromSpawnPoint(data);
        locationStore.setSingle(p.getUuid(), LocationType.SPAWN, bedLoc);
    }

    private static void registerNoclip(ServerPlayNetworkHandler handler) {
        var p = handler.player;
        NoclipCapability.setSupported(p, false);
        boolean on = playerStore.get(p.getUuid()).noClip();
        p.noClip = on;
        var ab = p.getAbilities();

        if (on) {
            ab.allowFlying = true;
            ab.flying = true;
        }

        p.fallDistance = 0;
        p.sendAbilitiesUpdate();
    }
}
