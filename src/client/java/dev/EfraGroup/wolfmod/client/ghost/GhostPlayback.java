package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.util.math.MathHelper;

public final class GhostPlayback {
    private GhostData ghostData;
    private boolean active;
    private int frameIndex;

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
        if (!active || ghostData == null || !Timer.isRunning()) {
            return null;
        }

        float targetTick = Timer.getElapsedMillis() / 50.0f;
        int frameCount = ghostData.frameCount();
        if (frameCount == 0) {
            return null;
        }

        while (frameIndex + 1 < frameCount && ghostData.ticks()[frameIndex + 1] <= targetTick) {
            frameIndex++;
        }

        if (frameIndex >= frameCount - 1) {
            active = false;
            return new GhostPose(
                    ghostData.xs()[frameCount - 1],
                    ghostData.ys()[frameCount - 1],
                    ghostData.zs()[frameCount - 1],
                    ghostData.yaws()[frameCount - 1],
                    ghostData.pitches()[frameCount - 1]
            );
        }

        int currentTick = ghostData.ticks()[frameIndex];
        int nextTick = ghostData.ticks()[frameIndex + 1];
        float progress = nextTick == currentTick ? 0.0f : MathHelper.clamp((targetTick - currentTick) / (float) (nextTick - currentTick), 0.0f, 1.0f);

        return new GhostPose(
                MathHelper.lerp(progress, ghostData.xs()[frameIndex], ghostData.xs()[frameIndex + 1]),
                MathHelper.lerp(progress, ghostData.ys()[frameIndex], ghostData.ys()[frameIndex + 1]),
                MathHelper.lerp(progress, ghostData.zs()[frameIndex], ghostData.zs()[frameIndex + 1]),
                MathHelper.lerpAngleDegrees(progress, ghostData.yaws()[frameIndex], ghostData.yaws()[frameIndex + 1]),
                MathHelper.lerp(progress, ghostData.pitches()[frameIndex], ghostData.pitches()[frameIndex + 1])
        );
    }
}
