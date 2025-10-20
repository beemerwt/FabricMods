// Permissions.java
package com.github.beemerwt.essence.permission;

import com.github.beemerwt.essence.Essence;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicReference;

public final class Permissions {
    private static final AtomicReference<PermissionsProvider> REF =
            new AtomicReference<>(new VanillaPermissionsProvider());

    private Permissions() {}

    // Call once during mod init to set up a safe provider and a refresh hook.
    public static void init() {
        REF.set(buildProviderSafely());
        // Once the server is fully started, re-evaluate in case optional mods are present.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> REF.set(buildProviderSafely()));
    }

    public static boolean check(ServerCommandSource src, String node, int opLevelFallback) {
        if (isJailed(src)) return false;
        return REF.get().check(src, node, opLevelFallback);
    }

    public static boolean check(ServerCommandSource src, String node, OpLevel fallback) {
        return check(src, node, fallback.level);
    }

    public static boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        if (isJailed(src)) return false;
        return REF.get().checkOrDefault(src, node, defIfNoPerms);
    }

    private static PermissionsProvider buildProviderSafely() {
        String permSystem = Essence.getConfig().permissions;
        try {
            // Prefer the Fabric Permissions API if present
            if (permSystem.equalsIgnoreCase("fabric")) {
                if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")
                        || classPresent("me.lucko.fabric.api.permissions.v0.Permissions")) {
                    return new FabricPermissionsProvider(); // tolerant reflection inside
                } else {
                    throw new IllegalStateException("Fabric Permissions API selected but not found! Falling back to vanilla permissions.");
                }
            }

            if (permSystem.equalsIgnoreCase("luckperms")) {
                if (FabricLoader.getInstance().isModLoaded("luckperms")
                        || classPresent("net.luckperms.api.LuckPerms")) {
                    return new LuckPermissionsProvider();
                } else {
                    throw new IllegalStateException("LuckPerms selected but not found! Falling back to vanilla permissions.");
                }
            }

            if (permSystem.equalsIgnoreCase("none")) {
                Essence.getLogger().warning("Not using a permissions system. Permissions checks will use op level.");
                Essence.getLogger().warning("If you wish to use a permissions system you can set it in the config.");
                Essence.getLogger().warning("If this was intentional then you can ignore this message.");
                return new VanillaPermissionsProvider();
            }
        } catch (Throwable e) {
            Essence.getLogger().error(e, "Error loading the selected permissions provider" +
                    "Falling back to vanilla permissions.");
        }

        return new VanillaPermissionsProvider();
    }

    private static boolean isJailed(ServerCommandSource src) {
        // Console / command blocks / non-player sources cannot be jailed.
        if (src.getPlayer() == null) return false;

        var player = src.getPlayer();
        boolean jailed = Essence.getSuspensionStore().isJailed(player.getUuid());
        boolean exempt = player.getPermissionLevel() >= 3; // admins+ cannot be jailed
        if (jailed && !exempt) {
            player.sendMessage(Text.literal("You are jailed."), false);
            return true; // block command
        }

        return false;
    }

    private static boolean classPresent(String fqcn) {
        try { Class.forName(fqcn, false, Permissions.class.getClassLoader()); return true; }
        catch (Throwable t) { return false; }
    }
}
