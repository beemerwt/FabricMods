package com.github.beemerwt.commands;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/** Minimal mirroring so /name has reasonable tab-complete even as a proxy. */
final class ChildMirror {
    private ChildMirror() {}

    static void mirrorLiteralChildren(LiteralCommandNode<ServerCommandSource> proxy,
                                      LiteralCommandNode<ServerCommandSource> target) {
        for (CommandNode<ServerCommandSource> child : target.getChildren()) {
            if (child instanceof LiteralCommandNode<?> litChild) {
                var n = CommandManager.literal(litChild.getName()).redirect(child).build();
                proxy.addChild(n);
            }
        }
    }
}

