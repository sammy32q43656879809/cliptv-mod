package com.cliptv.fabric;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C payload — the Paper plugin sends commands to the client mod.
 * Commands: "REC_START", "REC_STOP", "CLIP"
 */
public record ClipTVServerCommandPayload(String command) implements CustomPayload {

    public static final CustomPayload.Id<ClipTVServerCommandPayload> ID =
            new CustomPayload.Id<>(Identifier.of("clapcraft", "cliptv_cmd"));

    public static final PacketCodec<PacketByteBuf, ClipTVServerCommandPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING,
                    ClipTVServerCommandPayload::command,
                    ClipTVServerCommandPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
