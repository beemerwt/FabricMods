package com.github.beemerwt.shulkerplace.net;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public record PickFromShulkerPayload(int selectedHotbarSlot, Identifier itemId) implements CustomPayload {
    public static final Id<PickFromShulkerPayload> ID = new Id<>(Identifier.of("shulkerplace", "pick_request"));

    public static final PacketCodec<RegistryByteBuf, PickFromShulkerPayload> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT, PickFromShulkerPayload::selectedHotbarSlot,
            Identifier.PACKET_CODEC, PickFromShulkerPayload::itemId,
            PickFromShulkerPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public ItemStack asStack(int count) {
        var item = Registries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        stack.setCount(count);
        return stack;
    }
}
