package com.github.beemerwt.beacontweaks;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BeaconTweaks implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("BeaconTweaks");
    public static BTConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = BTConfig.loadOrCreate();
        LOGGER.info("[BeaconTweaks] tickInterval={}, boostAmplifier={}, includeSecondary={}, forceAllBeaconsRange={}",
                CONFIG.tickInterval, CONFIG.boostAmplifier, CONFIG.includeSecondary, CONFIG.forceAllBeaconsRange);

        // /beaconrange off
        // /beaconrange <radius>
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("beaconrange")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(literal("off").executes(ctx -> {
                        CONFIG.forceAllBeaconsRange = -1;
                        BTConfig.save(CONFIG);
                        ctx.getSource().sendFeedback(() -> Text.literal("[BeaconTweaks] Forced range disabled."), true);
                        return 1;
                    }))
                    .then(argument("radius", IntegerArgumentType.integer(0)).executes(ctx -> {
                        int r = IntegerArgumentType.getInteger(ctx, "radius");
                        CONFIG.forceAllBeaconsRange = r;
                        BTConfig.save(CONFIG);
                        ctx.getSource().sendFeedback(() -> Text.literal("[BeaconTweaks] Forced range set to " + r + "."), true);
                        return 1;
                    }))
            );
        });

    }
}
