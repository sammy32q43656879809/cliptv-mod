package com.cliptv.neoforge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: client announces ClipTV mod presence to the server. */
public record ClipTVHandshakePayload(String version) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClipTVHandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("clapcraft", "cliptv"));

    public static final StreamCodec<ByteBuf, ClipTVHandshakePayload> CODEC =
            ByteBufCodecs.STRING_UTF8.map(ClipTVHandshakePayload::new, ClipTVHandshakePayload::version);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
