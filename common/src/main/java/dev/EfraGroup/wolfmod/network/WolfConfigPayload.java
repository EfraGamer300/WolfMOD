package dev.EfraGroup.wolfmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WolfConfigPayload(String key, String value) implements CustomPayload {
    public static final Id<WolfConfigPayload> ID = new Id<>(Identifier.of("wolfnetwork", "settings"));

    // Codec Manual: Lê exatamente o que o Bukkit envia via VarInt
    public static final PacketCodec<PacketByteBuf, WolfConfigPayload> CODEC = new PacketCodec<>() {
        @Override
        public WolfConfigPayload decode(PacketByteBuf buf) {
            // readString(32767) consome o VarInt e os bytes da String
            String k = buf.readString(32767);
            String v = buf.readString(32767);

            // Garante que não sobre nada no buffer para evitar o erro de bytes extra
            if (buf.readableBytes() > 0) {
                buf.skipBytes(buf.readableBytes());
            }

            return new WolfConfigPayload(k, v);
        }

        @Override
        public void encode(PacketByteBuf buf, WolfConfigPayload value) {
            buf.writeString(value.key());
            buf.writeString(value.value());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}