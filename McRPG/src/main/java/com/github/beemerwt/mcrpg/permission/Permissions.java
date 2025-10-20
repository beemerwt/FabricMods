// Permissions.java
package com.github.beemerwt.mcrpg.permission;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;

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
        return REF.get().check(src, node, opLevelFallback);
    }

    public static boolean check(ServerCommandSource src, String node, OpLevel fallback) {
        return check(src, node, fallback.level);
    }

    public static boolean checkOrDefault(ServerCommandSource src, String node, boolean defIfNoPerms) {
        return REF.get().checkOrDefault(src, node, defIfNoPerms);
    }

    private static PermissionsProvider buildProviderSafely() {
        String permSystem = ConfigManager.getGeneralConfig().permissions;
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
                McRPG.getLogger().warning("Not using a permissions system. Permissions checks will use op level.");
                McRPG.getLogger().warning("If you wish to use a permissions system you can set it in the config.");
                McRPG.getLogger().warning("If this was intentional then you can ignore this message.");
                return new VanillaPermissionsProvider();
            }
        } catch (Throwable e) {
            McRPG.getLogger().error(e, "Error loading the selected permissions provider" +
                    "Falling back to vanilla permissions.");
        }

        return new VanillaPermissionsProvider();
    }

    private static boolean classPresent(String fqcn) {
        try { Class.forName(fqcn, false, Permissions.class.getClassLoader()); return true; }
        catch (Throwable t) { return false; }
    }
}
