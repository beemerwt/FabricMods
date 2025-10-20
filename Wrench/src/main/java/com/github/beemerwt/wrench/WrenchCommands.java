package com.github.beemerwt.wrench;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class WrenchCommands {

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(literal("wrench")
            .requires(src -> src.hasPermissionLevel(2) || hasPerm(src, "wrench.admin"))
            .then(literal("give")
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var p = src.getPlayer();
                    if (p == null) return 0;
                    giveWrench(p);
                    src.sendFeedback(() -> Text.literal("Gave wrench."), true);
                    return 1;
                })
            )
        );

        dispatcher.register(literal("wrench")
            .requires(src -> src.hasPermissionLevel(2))
            .then(literal("reload").executes(ctx -> {
                Wrench.CONFIG = WrenchConfig.load();
                ctx.getSource().sendFeedback(() -> Text.literal("[Wrench] Config reloaded."), false);
                return 1;
            }))
        );
    }

    private static boolean hasPerm(ServerCommandSource src, String node) {
        // Soft-perm: LuckPerms/Fabric Perms API if present, else fallback to op level.
        try {
            var clz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            var method = clz.getMethod("check", ServerCommandSource.class, String.class, int.class);
            Object ok = method.invoke(null, src, node, 2);
            return (boolean) ok;
        } catch (Throwable t) {
            return src.hasPermissionLevel(2);
        }
    }

    private static void giveWrench(ServerPlayerEntity p) {
        var stack = new ItemStack(Items.COPPER_INGOT);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Wrench"));
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, Wrench.MODEL_DATA_COMPONENT);
        p.giveOrDropStack(stack);
        p.playerScreenHandler.sendContentUpdates();
    }
}
