package com.github.beemerwt.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;

final class NamespacedAliases {
    private NamespacedAliases() {}

    /** Ensure "/namespace" literal exists at root. */
    static LiteralCommandNode<ServerCommandSource> ensureNamespaceRoot(
        CommandDispatcher<ServerCommandSource> dispatcher, String namespace) {
        var existing = dispatcher.getRoot().getChild(namespace);
        if (existing instanceof LiteralCommandNode<?> lit) {
            @SuppressWarnings("unchecked")
            var cast = (LiteralCommandNode<ServerCommandSource>) lit;
            return cast;
        }
        var nsNode = LiteralArgumentBuilder.<ServerCommandSource>literal(namespace).build();
        dispatcher.getRoot().addChild(nsNode);
        return nsNode;
    }

    /** Add `/ns:name` as a single-token alias that redirects to `target`. */
    static void addSingleTokenAlias(CommandDispatcher<ServerCommandSource> dispatcher,
                                    String namespace, String name,
                                    CommandNode<ServerCommandSource> target) {
        dispatcher.getRoot().addChild(
            LiteralArgumentBuilder.<ServerCommandSource>literal(namespace + ":" + name)
                .redirect(target)
                .build()
        );
    }

    /** Add minecraft:* aliases for any bare literal at root. */
    static void addMinecraftAliasForBare(CommandDispatcher<ServerCommandSource> dispatcher,
                                         LiteralCommandNode<ServerCommandSource> bare) {
        if (bare.getName().contains(":")) return;
        // redirect safely to the actual bare node
        // /minecraft:name
        addSingleTokenAlias(dispatcher, "minecraft", bare.getName(), bare);
        // /minecraft name (space form)
        var nsRoot = ensureNamespaceRoot(dispatcher, "minecraft");
        nsRoot.addChild(
            LiteralArgumentBuilder.<ServerCommandSource>literal(bare.getName())
                .redirect(bare)
                .build()
        );
    }
}
