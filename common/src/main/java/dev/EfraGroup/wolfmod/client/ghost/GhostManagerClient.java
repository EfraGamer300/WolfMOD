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
    private static GhostPlayer riderEntity;
    private static ClientWorld riderWorld;

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
        discardRider();
    }

    public static void clearGhost() {
        PLAYBACK.clear();
        discardBoat();
        discardRider();
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            PLAYBACK.stop();
            discardBoat();
            discardRider();
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
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
        tickRider(client, pose);
    }

    /**
     * Piloto falso como entidade real (espelha o BoatLabs): anda montado no
     * barco para os remos animarem, mas a posicao e forcada por tick no
     * assento (barco - altura) com yaw/pitch da gravacao.
     */
    private static void tickRider(MinecraftClient client, GhostPose pose) {
        if (boatEntity == null || client.world == null) {
            return;
        }
        if (riderEntity == null || riderWorld != client.world) {
            discardRider();
            riderWorld = client.world;
            riderEntity = new GhostPlayer(client.world);
            riderEntity.setNoGravity(true);
            riderEntity.noClip = true;
            riderEntity.setSilent(true);
            client.world.addEntity(riderEntity);
            riderEntity.startRiding(boatEntity, true);
        }
        riderEntity.setPosition(pose.x(), pose.y() - boatEntity.getHeight(), pose.z());
        riderEntity.setYaw(pose.yaw());
        riderEntity.setPitch(pose.pitch());
    }

    private static void discardBoat() {
        if (boatEntity != null) {
            boatEntity.discard();
            boatEntity = null;
        }
        boatWorld = null;
    }

    private static void discardRider() {
        if (riderEntity != null) {
            riderEntity.discard();
            riderEntity = null;
        }
        riderWorld = null;
    }
}
