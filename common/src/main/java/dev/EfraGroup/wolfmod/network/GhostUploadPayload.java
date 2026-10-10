package dev.EfraGroup.wolfmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server upload of a lap the player just drove, in binary.
 *
 * <p>This is the reverse of {@link GhostDataPayload}: same arrays, opposite direction. Binary
 * rather than the JSON config channel because a real lap holds thousands of frames, which as
 * JSON would be several times the practical plugin message limit.
 *
 * <p>Laps are sent in sequential chunks ({@link #chunkIndex}/{@link #chunkCount}) so no single
 * packet has to carry the whole trajectory. The server reassembles them in arrival order.
 */
public record GhostUploadPayload(
        byte version,
        String trackId,
        int chunkIndex,
        int chunkCount,
        int[] ticks,
        float[] xs,
        float[] ys,
        float[] zs,
        float[] yaws
) implements CustomPayload {

    public static final Id<GhostUploadPayload> ID = new Id<>(Identifier.of("wolfnetwork", "ghost_upload"));

    public static final PacketCodec<PacketByteBuf, GhostUploadPayload> CODEC = new PacketCodec<>() {
        @Override
        public GhostUploadPayload decode(PacketByteBuf buf) {
            byte version = buf.readByte();
            String trackId = buf.readString(32767);
            int chunkIndex = buf.readVarInt();
            int chunkCount = buf.readVarInt();
            int frameCount = buf.readVarInt();

            int[] ticks = new int[frameCount];
            float[] xs = new float[frameCount];
            float[] ys = new float[frameCount];
            float[] zs = new float[frameCount];
            float[] yaws = new float[frameCount];

            for (int i = 0; i < frameCount; i++) {
                ticks[i] = buf.readVarInt();
                xs[i] = buf.readFloat();
                ys[i] = buf.readFloat();
                zs[i] = buf.readFloat();
                yaws[i] = buf.readFloat();
            }

            if (buf.readableBytes() > 0) {
                buf.skipBytes(buf.readableBytes());
            }

            return new GhostUploadPayload(version, trackId, chunkIndex, chunkCount, ticks, xs, ys, zs, yaws);
        }

        @Override
        public void encode(PacketByteBuf buf, GhostUploadPayload value) {
            buf.writeByte(value.version());
            buf.writeString(value.trackId());
            buf.writeVarInt(value.chunkIndex());
            buf.writeVarInt(value.chunkCount());
            buf.writeVarInt(value.ticks().length);

            for (int i = 0; i < value.ticks().length; i++) {
                buf.writeVarInt(value.ticks()[i]);
                buf.writeFloat(value.xs()[i]);
                buf.writeFloat(value.ys()[i]);
                buf.writeFloat(value.zs()[i]);
                buf.writeFloat(value.yaws()[i]);
            }
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}