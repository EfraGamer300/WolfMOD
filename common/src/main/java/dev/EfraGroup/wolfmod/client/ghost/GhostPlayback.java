package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.util.math.MathHelper;

public final class GhostPlayback {
    private volatile GhostData ghostData;
    private volatile boolean active;
    private volatile int frameIndex;

    public void setGhostData(GhostData ghostData) {
        this.ghostData = ghostData;
        this.frameIndex = 0;
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

    public GhostPose samplePose() {
        if (!active) {
            return null;
        }

        GhostData data = ghostData;
        if (data == null || !Timer.isRunning()) {
            return null;
        }

        float targetTick = Timer.getElapsedMillis() / 50.0f;
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
