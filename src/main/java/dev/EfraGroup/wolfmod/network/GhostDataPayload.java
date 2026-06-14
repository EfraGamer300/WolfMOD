package dev.EfraGroup.wolfmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GhostDataPayload(
        byte version,
        String trackId,
        long lapTimeMs,
        int sampleIntervalTicks,
        int frameCount,
        int[] ticks,
        float[] xs,
        float[] ys,
        float[] zs,
        float[] yaws,
        float[] pitches
) implements CustomPayload {
    public static final Id<GhostDataPayload> ID = new Id<>(Identifier.of("wolfnetwork", "ghost_data"));

    public static final PacketCodec<PacketByteBuf, GhostDataPayload> CODEC = new PacketCodec<>() {
        @Override
        public GhostDataPayload decode(PacketByteBuf buf) {
            byte version = buf.readByte();
            String trackId = buf.readString(32767);
            long lapTimeMs = buf.readLong();
            int sampleIntervalTicks = buf.readVarInt();
            int frameCount = buf.readVarInt();

            int[] ticks = new int[frameCount];
            float[] xs = new float[frameCount];
            float[] ys = new float[frameCount];
            float[] zs = new float[frameCount];
            float[] yaws = new float[frameCount];
            float[] pitches = new float[frameCount];

            for (int i = 0; i < frameCount; i++) {
                ticks[i] = buf.readVarInt();
                xs[i] = buf.readFloat();
                ys[i] = buf.readFloat();
                zs[i] = buf.readFloat();
                yaws[i] = buf.readFloat();
                pitches[i] = buf.readFloat();
            }

            if (buf.readableBytes() > 0) {
                buf.skipBytes(buf.readableBytes());
            }

            return new GhostDataPayload(version, trackId, lapTimeMs, sampleIntervalTicks, frameCount, ticks, xs, ys, zs, yaws, pitches);
        }

        @Override
        public void encode(PacketByteBuf buf, GhostDataPayload value) {
            buf.writeByte(value.version());
            buf.writeString(value.trackId());
            buf.writeLong(value.lapTimeMs());
            buf.writeVarInt(value.sampleIntervalTicks());
            buf.writeVarInt(value.frameCount());

            for (int i = 0; i < value.frameCount(); i++) {
                buf.writeVarInt(value.ticks()[i]);
                buf.writeFloat(value.xs()[i]);
                buf.writeFloat(value.ys()[i]);
                buf.writeFloat(value.zs()[i]);
                buf.writeFloat(value.yaws()[i]);
                buf.writeFloat(value.pitches()[i]);
            }
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
