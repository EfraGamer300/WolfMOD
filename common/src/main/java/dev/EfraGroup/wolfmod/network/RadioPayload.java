package dev.EfraGroup.wolfmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RadioPayload(String command) implements CustomPayload {
    public static final Id<RadioPayload> ID = new Id<>(Identifier.of("formularacing", "radio"));

    public static final PacketCodec<PacketByteBuf, RadioPayload> CODEC = new PacketCodec<>() {
        @Override
        public RadioPayload decode(PacketByteBuf buf) {
            String cmd = buf.readString(32767);
            if (buf.readableBytes() > 0) {
                buf.skipBytes(buf.readableBytes());
            }
            return new RadioPayload(cmd);
        }

        @Override
        public void encode(PacketByteBuf buf, RadioPayload value) {
            buf.writeString(value.command());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
