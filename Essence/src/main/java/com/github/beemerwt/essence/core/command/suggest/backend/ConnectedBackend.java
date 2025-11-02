package com.github.beemerwt.essence.core.command.suggest.backend;

import net.minecraft.server.MinecraftServer;

public class ConnectedBackend implements Backend {
    @Override
    public int count(MinecraftServer server, String prefix) {
        var list = server.getPlayerManager().getPlayerList();
        if (prefix == null || prefix.isEmpty()) return list.size();
        final String q = prefix.toLowerCase();
        return (int) list.stream()
            .map(pl -> pl.getName().getString())
            .filter(name -> name.toLowerCase().startsWith(q))
            .count();
    }

    @Override
    public void streamNames(MinecraftServer server, String prefix, int offset, int limit,
                            java.util.function.Consumer<String> sink) {
        var stream = server.getPlayerManager().getPlayerList().stream()
            .map(pl -> pl.getName().getString());

        if (prefix != null && !prefix.isEmpty()) {
            final String q = prefix.toLowerCase();
            stream = stream.filter(name -> name.toLowerCase().startsWith(q));
        }

        stream.skip(offset).limit(limit).forEach(sink);
    }
}
