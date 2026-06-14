package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.network.GhostDataPayload;

public final class GhostManagerClient {
    private static final GhostPlayback PLAYBACK = new GhostPlayback();
    private static boolean enabled = true;

    private GhostManagerClient() {
    }

    public static void loadGhost(GhostDataPayload payload) {
        PLAYBACK.setGhostData(new GhostData(
                payload.trackId(),
                payload.lapTimeMs(),
                payload.sampleIntervalTicks(),
                payload.frameCount(),
                payload.ticks(),
                payload.xs(),
                payload.ys(),
                payload.zs(),
                payload.yaws(),
                payload.pitches()
        ));
    }

    public static void startPlayback() {
        if (enabled) {
            PLAYBACK.start();
        }
    }

    public static void stopPlayback() {
        PLAYBACK.stop();
    }

    public static void clearGhost() {
        PLAYBACK.clear();
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            PLAYBACK.stop();
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static GhostPose currentPose() {
        if (!enabled) {
            return null;
        }
        return PLAYBACK.samplePose();
    }
}
