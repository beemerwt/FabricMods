package com.github.beemerwt.essence.core.payload;

import com.github.beemerwt.essence.core.Essence;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NoclipCapC2SPayload() implements CustomPayload {
    public static final Id<NoclipCapC2SPayload> ID =
        new Id<>(Identifier.of(Essence.MOD_ID, "noclip_capability"));

    public static final PacketCodec<ByteBuf, NoclipCapC2SPayload> CODEC =
        PacketCodec.unit(new NoclipCapC2SPayload());

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
