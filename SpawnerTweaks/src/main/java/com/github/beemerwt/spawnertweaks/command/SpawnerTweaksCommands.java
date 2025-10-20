package com.github.beemerwt.spawnertweaks.command;

import com.github.beemerwt.spawnertweaks.config.ConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SpawnerTweaksCommands {
    private SpawnerTweaksCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("spawnertweaks")
                        .requires(src -> src.hasPermissionLevel(2)) // OP level 2+
                        .then(CommandManager.literal("reload")
                                .executes(ctx -> {
                                    try {
                                        ConfigManager.load();
                                        ctx.getSource().sendFeedback(() -> Text.literal("[SpawnerTweaks] Config reloaded."), true);
                                        return 1;
                                    } catch (Exception e) {
                                        ctx.getSource().sendError(Text.literal("[SpawnerTweaks] Reload failed: " + e.getMessage()));
                                        return 0;
                                    }
                                })
                        )
        );
    }
}
