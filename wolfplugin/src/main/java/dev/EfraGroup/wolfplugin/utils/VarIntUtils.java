package dev.EfraGroup.wolfplugin.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class VarIntUtils {
    private VarIntUtils() {
    }

    public record DecodedStrings(String key, String value) {
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt muito grande");
            }
        } while ((currentByte & 0x80) != 0);
        return value;
    }

    public static byte[] encodeString(String key, String value) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(keyBytes.length + valueBytes.length + 10);
            DataOutputStream out = new DataOutputStream(baos);

            writeVarInt(out, keyBytes.length);
            out.write(keyBytes);

            writeVarInt(out, valueBytes.length);
            out.write(valueBytes);

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao codificar payload", e);
        }
    }

    public static DecodedStrings decodeStrings(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

            int keyLen = readVarInt(in);
            byte[] keyBytes = new byte[keyLen];
            in.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            int valueLen = readVarInt(in);
            byte[] valueBytes = new byte[valueLen];
            in.readFully(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);

            return new DecodedStrings(key, value);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao decodificar payload", e);
        }
    }
}

