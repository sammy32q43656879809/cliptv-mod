package com.cliptv.fabric;

import com.cliptv.ClipTVConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S payload — sent by the client on server join to identify itself as
 * a ClipTV-capable client. The Paper plugin detects this channel registration.
 */
public record ClipTVHandshakePayload(String version) implements CustomPayload {

    public static final CustomPayload.Id<ClipTVHandshakePayload> ID =
            new CustomPayload.Id<>(Identifier.of("clapcraft", "cliptv"));

    public static final PacketCodec<PacketByteBuf, ClipTVHandshakePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING,
                    ClipTVHandshakePayload::version,
                    ClipTVHandshakePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
