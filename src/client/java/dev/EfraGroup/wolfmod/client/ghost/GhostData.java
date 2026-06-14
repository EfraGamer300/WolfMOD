package dev.EfraGroup.wolfmod.client.ghost;

public record GhostData(
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
) {
}
