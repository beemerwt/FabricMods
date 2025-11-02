package com.github.beemerwt.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;

public final class CommandNamespaces {
    private CommandNamespaces() {}

    /** Add both "ns:cmd" and "ns cmd" aliases that redirect to target. */
    public static void addAliases(CommandDispatcher<ServerCommandSource> dispatcher,
                                  String namespace,
                                  String commandName,
                                  CommandNode<ServerCommandSource> target) {
        // /ns:cmd
        dispatcher.getRoot().addChild(
            LiteralArgumentBuilder.<ServerCommandSource>literal(namespace + ":" + commandName)
                .redirect(target)
                .build()
        );

        // /ns cmd
        var nsNode = ensureNamespaceRoot(dispatcher, namespace);
        nsNode.addChild(
            LiteralArgumentBuilder.<ServerCommandSource>literal(commandName)
                .redirect(target)
                .build()
        );
    }

    /** Ensure a "/namespace ..." literal exists and returns it. */
    private static LiteralCommandNode<ServerCommandSource> ensureNamespaceRoot(
        CommandDispatcher<ServerCommandSource> dispatcher, String namespace) {
        var existing = dispatcher.getRoot().getChild(namespace);
        if (existing instanceof LiteralCommandNode<ServerCommandSource> lit) return lit;

        var nsNode = LiteralArgumentBuilder.<ServerCommandSource>literal(namespace).build();
        dispatcher.getRoot().addChild(nsNode);
        return nsNode;
    }
}

