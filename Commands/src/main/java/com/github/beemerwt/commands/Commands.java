package com.github.beemerwt.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;

import java.util.ArrayList;
import java.util.Objects;

/** Public API: authors static-import literal(ns, name). */
public final class Commands implements ModInitializer {
    /** Authors use: literal("essence", "tp").then(...).executes(...).register(dispatcher) */
    public static NsLiteral literal(String namespace, String name) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        return new NsLiteral(namespace, name);
    }

    @Override
    public void onInitialize() {
        // 1) After all commands are registered, add /minecraft:<name> for every bare literal.
        ServerLifecycleEvents.SERVER_STARTED.register(Commands::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        var dispatcher = server.getCommandManager().getDispatcher();
        var root = dispatcher.getRoot();

        // snapshot bare literals to avoid concurrent modification
        var bareLiterals = new ArrayList<LiteralCommandNode<ServerCommandSource>>();
        for (var child : root.getChildren()) {
            if (child instanceof LiteralCommandNode<?> lit && lit.getName().indexOf(':') < 0) {
                @SuppressWarnings("unchecked")
                var bare = (LiteralCommandNode<ServerCommandSource>) lit;
                bareLiterals.add(bare);
            }
        }

        // Now it's safe to add children to root while iterating our snapshot.
        for (var bare : bareLiterals) {
            String rawName = bare.getName();
            String bareName = sanitizeBare(rawName);
            if (bareName.isEmpty()) continue;

            String aliasName = "minecraft:" + bareName;

            // Skip if someone already added this alias
            if (root.getChild(aliasName) == null) {
                root.addChild(CommandManager.literal(aliasName).redirect(bare).build());
            }

            // Seed router default to vanilla unless a mod later overrides it.
            CommandRouter.initDefault(bareName, aliasName);
        }
    }

    private static String sanitizeBare(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '/') i++;
        return s.substring(i);
    }

    /** Our builder: canonical literal is the single token "ns:name" (colon form only). */
    public static final class NsLiteral extends LiteralArgumentBuilder<ServerCommandSource> {
        private final String namespace;
        private final String name;   // bare (e.g., "tp")
        private final String fqn;    // ns:name

        private NsLiteral(String namespace, String name) {
            super(namespace + ":" + name);
            this.namespace = namespace;
            this.name = name;
            this.fqn = namespace + ":" + name;
        }

        /** Register canonical `/ns:name` and make it the current default for bare `/name`. */
        public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
            // 1) Add canonical node at root: "/ns:name"
            var target = this.build();
            dispatcher.getRoot().addChild(target);

            // 2) Update router so bare "/name" resolves to this implementation (last wins).
            CommandRouter.setDefault(name, fqn);
        }
    }
}
