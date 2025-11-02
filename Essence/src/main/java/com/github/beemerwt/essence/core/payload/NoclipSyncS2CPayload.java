package com.github.beemerwt.essence.core.payload;

import com.github.beemerwt.essence.core.Essence;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NoclipSyncS2CPayload(boolean on) implements CustomPayload {
    public static final Id<NoclipSyncS2CPayload> ID =
        new Id<>(Identifier.of(Essence.MOD_ID, "noclip_sync"));

    public static final PacketCodec<ByteBuf, NoclipSyncS2CPayload> CODEC =
        PacketCodecs.BOOLEAN.xmap(NoclipSyncS2CPayload::new, NoclipSyncS2CPayload::on);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
