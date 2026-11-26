package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.util.math.MathHelper;

public final class GhostPlayback {
    private volatile GhostData ghostData;
    private volatile boolean active;
    private volatile int frameIndex;
    private volatile int playbackOffsetTicks;

    public void setGhostData(GhostData ghostData) {
        this.ghostData = ghostData;
        this.frameIndex = 0;
    }

    public void setPlaybackOffset(int ticks) {
        this.playbackOffsetTicks = ticks;
    }

    public void start() {
        this.frameIndex = 0;
        this.active = ghostData != null && ghostData.frameCount() > 0;
    }

    public void stop() {
        this.active = false;
        this.frameIndex = 0;
    }

    public void clear() {
        this.active = false;
        this.frameIndex = 0;
        this.ghostData = null;
    }

    public boolean hasData() {
        GhostData data = this.ghostData;
        return data != null && data.frameCount() > 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public int frameCount() {
        GhostData data = this.ghostData;
        return data == null ? 0 : data.frameCount();
    }

    public GhostPose samplePose(float tickDelta) {
        if (!active) {
            return null;
        }

        GhostData data = ghostData;
        if (data == null || !Timer.isRunning()) {
            return null;
        }

        // The frames are stamped in lap ticks and the Timer is the lap clock. Anchoring the
        // ghost on the world tick instead made it drift: the anchor was captured whenever
        // playback happened to start (track load, late ghost payload), not when the lap did.
        float targetTick = Timer.getElapsedNanos() / 50_000_000.0f + tickDelta + playbackOffsetTicks;
        int frameCount = data.frameCount();
        if (frameCount == 0) {
            return null;
        }

        while (frameIndex + 1 < frameCount && data.ticks()[frameIndex + 1] <= targetTick) {
            frameIndex++;
        }

        if (frameIndex >= frameCount - 1) {
            active = false;
            return new GhostPose(
                    data.xs()[frameCount - 1],
                    data.ys()[frameCount - 1],
                    data.zs()[frameCount - 1],
                    data.yaws()[frameCount - 1],
                    data.pitches()[frameCount - 1]
            );
        }

        int currentTick = data.ticks()[frameIndex];
        int nextTick = data.ticks()[frameIndex + 1];
        float progress = nextTick == currentTick ? 0.0f : MathHelper.clamp((targetTick - currentTick) / (float) (nextTick - currentTick), 0.0f, 1.0f);

        return new GhostPose(
                MathHelper.lerp(progress, data.xs()[frameIndex], data.xs()[frameIndex + 1]),
                MathHelper.lerp(progress, data.ys()[frameIndex], data.ys()[frameIndex + 1]),
                MathHelper.lerp(progress, data.zs()[frameIndex], data.zs()[frameIndex + 1]),
                MathHelper.lerpAngleDegrees(progress, data.yaws()[frameIndex], data.yaws()[frameIndex + 1]),
                MathHelper.lerp(progress, data.pitches()[frameIndex], data.pitches()[frameIndex + 1])
        );
    }
}
