package com.github.beemerwt.essence.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class LiteralShim {

    /**
     * Temporary as I modify the CommandManagerMixin for Commands library
     * @param namespace
     * @param literal
     * @return
     */
    public static LiteralArgumentBuilder<ServerCommandSource> commandLiteral(String namespace, String literal) {
        return CommandManager.literal(literal);
    }
}
