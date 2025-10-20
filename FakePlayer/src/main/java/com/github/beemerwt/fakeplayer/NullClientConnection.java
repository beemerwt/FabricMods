package com.github.beemerwt.fakeplayer;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class NullClientConnection extends ClientConnection {
    private volatile boolean open = true;
    private final SocketAddress addr = new InetSocketAddress("0.0.0.0", 0);

    public NullClientConnection() {
        super(NetworkSide.SERVERBOUND);
        attachDummyChannel();
    }

    private void attachDummyChannel() {
        try {
            // Find the Channel field regardless of obf name (e.g., field_11651)
            Field chField = null;
            for (Field f : ClientConnection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    chField = f;
                    break;
                }
            }
            if (chField != null) {
                chField.setAccessible(true);
                // EmbeddedChannel safely no-ops writeAndFlush
                chField.set(this, new EmbeddedChannel());
            }
        } catch (Throwable ignored) {
            // If we can't set it, our send() overrides below still drop packets,
            // but some vanilla paths may still touch the field directly.
        }
    }

    @Override
    public void setInitialPacketListener(PacketListener listener) {
        // bypass vanilla's validation; do not call super
    }

    // New "master" send signature on 1.21.x
    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        // Drop; do NOT interact with the channel future listener
    }

    // Keep these shims if they exist in your mappings
    @Override
    public void send(Packet<?> packet) {
        send(packet, null, true);
    }

    @Override
    public void tick() { /* no-op */ }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public void disconnect(Text reason) { open = false; }

    @Override
    public void handleDisconnection() { open = false; }

    @Override
    public SocketAddress getAddress() { return addr; }
}
