package dev.EfraGroup.wolfmod.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Client-side horn, mirroring the BoatLabs honk. Plays a short horn sound locally
 * with a small cooldown so it cannot be spammed. A server-side broadcast would
 * require WolfPlugin support; this keeps the feature self-contained on the client.
 */
public final class HonkManager {
    private static long lastHonkNanos = 0L;
    private static final long COOLDOWN_NANOS = 500_000_000L;

    private HonkManager() {
    }

    public static void tryHonk() {
        long now = System.nanoTime();
        if (now - lastHonkNanos < COOLDOWN_NANOS) {
            return;
        }
        lastHonkNanos = now;
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        try {
            client.getSoundManager().play(new PositionedSoundInstance(
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.PLAYERS,
                    1.0f,
                    0.8f,
                    SoundInstance.createRandom(),
                    player.getBlockPos()));
        } catch (Throwable ignored) {
            // Keep the client alive even if the sound API differs across targets.
        }
    }
}
