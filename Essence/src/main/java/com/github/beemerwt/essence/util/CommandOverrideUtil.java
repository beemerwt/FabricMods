package com.github.beemerwt.essence.util;

import com.github.beemerwt.essence.Essence;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.Field;
import java.util.Map;

public final class CommandOverrideUtil {
    private CommandOverrideUtil() {}

    @SuppressWarnings("unchecked")
    public static void removeLiteral(CommandDispatcher<ServerCommandSource> dispatcher, String literal) {
        try {
            CommandNode<ServerCommandSource> root = dispatcher.getRoot();
            Field fChildren = CommandNode.class.getDeclaredField("children");
            Field fLiterals = CommandNode.class.getDeclaredField("literals");
            fChildren.setAccessible(true);
            fLiterals.setAccessible(true);
            Map<String, CommandNode<ServerCommandSource>> children =
                    (Map<String, CommandNode<ServerCommandSource>>) fChildren.get(root);
            Map<String, LiteralCommandNode<ServerCommandSource>> literals =
                    (Map<String, LiteralCommandNode<ServerCommandSource>>) fLiterals.get(root);

            children.remove(literal);
            literals.remove(literal);
        } catch (ReflectiveOperationException e) {
            // Not fatal; soft-override still works (Brigadier will merge and your executor will win)
            Essence.getLogger().error(e, "Could not hard-remove /" + literal + " (falling back to soft override)");
        }
    }
}

