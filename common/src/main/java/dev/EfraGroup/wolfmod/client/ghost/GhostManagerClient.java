package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

public final class GhostManagerClient {
    private static final GhostPlayback PLAYBACK = new GhostPlayback();
    private static volatile boolean enabled = true;

    private static GhostBoat boatEntity;
    private static ClientWorld boatWorld;

    private GhostManagerClient() {
    }

    public static void loadGhost(GhostDataPayload payload) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
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
        if (enabled) {
            startPlayback();
        }
    }

    public static void startPlayback() {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int worldTick = client.world != null ? (int) client.world.getTime() : 0;
        PLAYBACK.start(worldTick);
    }

    public static void stopPlayback() {
        PLAYBACK.stop();
        discardBoat();
    }

    public static void clearGhost() {
        PLAYBACK.clear();
        discardBoat();
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            PLAYBACK.stop();
            discardBoat();
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static GhostPose currentPose(int worldTick, float tickDelta) {
        if (!enabled) {
            return null;
        }
        return PLAYBACK.samplePose(worldTick, tickDelta);
    }

    /**
     * Entidade real do barco ghost no client world (espelha o BoatLabs).
     * O vanilla renderiza e interpola com tickDelta, sem o glitch do
     * render manual relativo a camera. Criada sob demanda no tick.
     */
    public static GhostBoat getBoat() {
        return boatEntity;
    }

    /** Chamado no fim do client tick: posiciona o barco a partir do playback. */
    public static void tickGhost(MinecraftClient client) {
        if (!enabled || client.world == null) {
            return;
        }
        GhostPose pose = PLAYBACK.samplePose((int) client.world.getTime(), 0.0f);
        if (pose == null) {
            return;
        }
        if (boatEntity == null || boatWorld != client.world) {
            discardBoat();
            boatWorld = client.world;
            boatEntity = new GhostBoat(client.world);
            boatEntity.setNoGravity(true);
            boatEntity.noClip = true;
            boatEntity.setSilent(true);
            client.world.addEntity(boatEntity);
        }
        boatEntity.setPosition(pose.x(), pose.y(), pose.z());
        boatEntity.setYaw(pose.yaw());
        boatEntity.setPaddlesMoving(true, true);
    }

    private static void discardBoat() {
        if (boatEntity != null) {
            boatEntity.discard();
            boatEntity = null;
        }
        boatWorld = null;
    }
}
