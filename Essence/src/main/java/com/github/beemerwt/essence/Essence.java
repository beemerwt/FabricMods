package com.github.beemerwt.essence;

import com.github.beemerwt.essence.command.*;
import com.github.beemerwt.essence.data.model.LocationStore;
import com.github.beemerwt.essence.data.LocationType;
import com.github.beemerwt.essence.data.model.PlayerStore;
import com.github.beemerwt.essence.data.model.SuspensionStore;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.Locations;
import com.github.beemerwt.essence.util.Teleporter;
import com.github.beemerwt.util.FabricLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public final class Essence implements ModInitializer {
    private static final EssenceConfig CONFIG = new EssenceConfig();
    public static EssenceConfig getConfig() { return CONFIG; }

    private static final PlayerStore PLAYER_STORE = PlayerStore.create();
    public static PlayerStore getPlayerStore() { return PLAYER_STORE; }

    private static final LocationStore LOCATION_STORE = LocationStore.create();
    public static LocationStore getLocationStore() { return LOCATION_STORE; }

    private static final SuspensionStore SUSPENSION_STORE = SuspensionStore.create();
    public static SuspensionStore getSuspensionStore() { return SUSPENSION_STORE; }

    private static final FabricLogger LOG = FabricLogger.getLogger("Essence");
    public static FabricLogger getLogger() { return LOG; }

    private static MinecraftServer server;
    public static @NotNull MinecraftServer getServer() { return server; }

    @Override
    public void onInitialize() {
        LOG.info("Essence initializing...");
        // PlayerEvents.register();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, regAccess, env) -> {
            HomeCommands.register(dispatcher);
            SpawnCommand.register(dispatcher);
            BackCommand.register(dispatcher);
            WarpCommands.register(dispatcher);
            TpaCommands.register(dispatcher);
            EnchantCommand.register(dispatcher);

            // Management
            JailCommands.register(dispatcher);
            BanCommands.register(dispatcher);
            MuteCommands.register(dispatcher);
            KickCommand.register(dispatcher);
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
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Permissions.init(); // Load permissions after player data is loaded
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            Essence.server = null
        );

        // Load per-player data on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            PLAYER_STORE.get(p); // Load player data

            var respawn = p.getRespawn();
            if (respawn == null)
                return;

            var loc = Locations.fromSpawnPoint(respawn.respawnData());
            LOCATION_STORE.setSingle(p.getUuid(), LocationType.SPAWN, loc);
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (SUSPENSION_STORE.isMuted(sender.getUuid())) {
                var m = SUSPENSION_STORE.getActiveMute(sender.getUuid()).orElse(null);
                String until = (m != null && m.expiresAt() != null) ? " until " + m.expiresAt() : " permanently";
                sender.sendMessage(Text.literal("You are muted" + until + "."
                        + (m != null ? " Reason: " + m.reason() : "")), false);
                return false; // block the chat message
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var store = getSuspensionStore();
            var now = Instant.now();
            for (var player : server.getPlayerManager().getPlayerList()) {
                var jail = store.getActiveJail(player.getUuid());
                if (jail.isEmpty()) continue;
                var jr = jail.get();
                if (jr.expiresAt().isBefore(now)) {
                    store.unjail(player.getUuid());
                    continue;
                }

                store.getJail(jr.jailName()).ifPresent(loc -> {
                    // if player strayed, snap back (you can add a threshold)
                    if (!player.getEntityWorld().getRegistryKey().getValue().toString().equals(loc.worldKey())
                            || player.squaredDistanceTo(loc.x(), loc.y(), loc.z()) > 4.0) {
                        var world = server.getWorld(RegistryKey.of(
                                RegistryKeys.WORLD, Identifier.of(loc.worldKey())));
                        if (world != null)
                            Teleporter.teleportTo(player, loc);
                    }
                });
            }
        });

        // Ensure spawn data exists
        LOG.info("Essence initialized.");
    }
}
