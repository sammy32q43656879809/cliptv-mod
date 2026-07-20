package com.cliptv.neoforge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: server instructs the client mod to start/stop recording or save a clip. */
public record ClipTVServerCommandPayload(String command) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClipTVServerCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("clapcraft", "cliptv_cmd"));

    public static final StreamCodec<ByteBuf, ClipTVServerCommandPayload> CODEC =
            ByteBufCodecs.STRING_UTF8.map(ClipTVServerCommandPayload::new, ClipTVServerCommandPayload::command);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
