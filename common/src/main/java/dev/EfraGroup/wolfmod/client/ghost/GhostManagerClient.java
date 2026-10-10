package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.GhostConfig;
import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Holds the personal best the mod is currently replaying and mirrors it to a local cache.
 *
 * <p>Local-first: the server sends a lap once and it is written to disk, so later visits to
 * the same track replay instantly with no download. The server remains authoritative — it
 * overwrites the entry whenever it has something newer, and {@link #loadCached(String)}
 * is only consulted when the player starts a run and no fresh copy has arrived yet.
 */
public final class GhostManagerClient {

    /** Cap on frames uploaded per lap, mirroring the server payload cap. */
    private static final int MAX_FRAMES = 2000;

    private static final GhostPlayback PLAYBACK = new GhostPlayback();
    private static volatile boolean enabled = true;

    private static GhostBoat boatEntity;
    private static ClientWorld boatWorld;
    private static GhostPlayer riderEntity;
    private static ClientWorld riderWorld;

    /** Track id of the lap currently held, used to key the local cache. */
    private static volatile String currentTrackId;

    /** Personal best lap time per track, in milliseconds, for local comparison on finish. */
    private static final Map<String, Long> LOCAL_BEST_MILLIS = Collections.synchronizedMap(new LinkedHashMap<>());

    /** Lap just driven on this track, uploaded to the server once the run completes. */
    private static final List<LocalFrame> pendingUpload = Collections.synchronizedList(new ArrayList<>());

    private static volatile String pendingUploadTrackId;

    private GhostManagerClient() {
    }

    /** A single recorded position of the player's boat, in the client's own clock. */
    public record LocalFrame(int tick, float x, float y, float z, float yaw) {
    }

/**
     * Position-only view of the current ghost, rebuilt when the ghost changes.
     *
     * <p>Cached because it is read every frame while rendering; rebuilding it each frame would
     * allocate an array per frame for no reason.
     */
private static volatile GhostLineRenderer.GhostLine currentLine;

    /** Height above the recorded boat position. One block up so the trail stays readable
     *  over ice, water and low terrain instead of being swallowed by it. */
    private static final double LINE_HEIGHT_OFFSET = 1.0;

    public static void loadGhost(GhostDataPayload payload) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
        GhostData data = new GhostData(
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
        );
        apply(data);
    }

    private static void apply(GhostData data) {
        PLAYBACK.setGhostData(data);
        currentTrackId = data.trackId();
        currentLine = GhostLineRenderer.GhostLine.from(
                data.xs(), data.ys(), data.zs(),
                GhostConfig.get().lineColor, LINE_HEIGHT_OFFSET);
        if (data.trackId() != null && data.lapTimeMs() > 0L) {
            LOCAL_BEST_MILLIS.put(data.trackId(), data.lapTimeMs());
        }
        // Write-through: the server copy is cached locally so the next visit needs no download.
        LocalGhostStore.save(data.trackId(), data);
        if (enabled) {
            startPlayback();
        }
    }

    /**
     * Replays the locally cached lap for {@code trackId} without contacting the server.
     * Returns false when nothing is cached, so the caller can wait for the server copy.
     */
    public static boolean loadCached(String trackId) {
        if (!ServerInfoManager.isOnWolfNetwork()) return false;
        GhostData cached = LocalGhostStore.load(trackId);
        if (cached == null) {
            return false;
        }
        PLAYBACK.setGhostData(cached);
        currentTrackId = trackId;
        currentLine = GhostLineRenderer.GhostLine.from(
                cached.xs(), cached.ys(), cached.zs(),
                GhostConfig.get().lineColor, LINE_HEIGHT_OFFSET);
        if (cached.lapTimeMs() > 0L) {
            LOCAL_BEST_MILLIS.put(trackId, cached.lapTimeMs());
        }
        if (enabled) {
            MinecraftClient client = MinecraftClient.getInstance();
            int worldTick = client.world != null ? (int) client.world.getTime() : 0;
            PLAYBACK.start(worldTick);
        }
        return true;
    }

    /** Cached personal best lap time for {@code trackId} in millis, or 0 when unknown. */
    public static long cachedBestMillis(String trackId) {
        Long value = trackId == null ? null : LOCAL_BEST_MILLIS.get(trackId);
        return value == null ? 0L : value;
    }

    /** Position-only view of the current ghost, for the ground trail. */
    public static GhostLineRenderer.GhostLine currentLine() {
        return currentLine;
    }

    /** Whether the ground trail is enabled in the config. */
    public static boolean isLineVisible() {
        return GhostConfig.get().renderLine;
    }

    /** Flips the ground trail and persists the choice. Returns the new state. */
    public static boolean toggleLine() {
        GhostConfig config = GhostConfig.get();
        config.renderLine = !config.renderLine;
        config.save();
        return config.renderLine;
    }

    /** Begins capturing the current lap for later upload to the server. */
    public static void beginRecording(String trackId) {
        if (trackId == null || trackId.isBlank()) return;
        pendingUploadTrackId = trackId;
        pendingUpload.clear();
    }

    /** Appends one frame of the in-progress lap, keeping the list bounded. */
    public static void recordFrame(int tick, float x, float y, float z, float yaw) {
        if (pendingUploadTrackId == null) return;
        if (pendingUpload.size() >= MAX_FRAMES) return;
        pendingUpload.add(new LocalFrame(tick, x, y, z, yaw));
    }

    /** Discards the in-progress lap, used when a run is aborted or reset. */
    public static void cancelRecording() {
        pendingUploadTrackId = null;
        pendingUpload.clear();
    }

    /**
     * The frames captured since {@link #beginRecording}, thinned evenly to
     * {@link #MAX_FRAMES} while keeping the first and last. Returns an empty list when
     * nothing usable was captured.
     */
    public static List<LocalFrame> finishRecording() {
        String trackId = pendingUploadTrackId;
        List<LocalFrame> frames;
        synchronized (pendingUpload) {
            frames = new ArrayList<>(pendingUpload);
        }
        pendingUploadTrackId = null;
        pendingUpload.clear();
        if (trackId == null || frames.size() < 2) {
            return List.of();
        }
        if (frames.size() > MAX_FRAMES) {
            frames = downsample(frames, MAX_FRAMES);
        }
        return frames;
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
        currentTrackId = null;
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

    public static String currentTrackId() {
        return currentTrackId;
    }

    /** Pose atual do ghost, ou null quando desligado/sem mundo. */
    public static GhostPose currentPose() {
        if (!enabled) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        return PLAYBACK.samplePose((int) client.world.getTime(), 0.0f);
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

    private static List<LocalFrame> downsample(List<LocalFrame> frames, int maxFrames) {
        int size = frames.size();
        List<LocalFrame> sampled = new ArrayList<>(maxFrames);
        double step = (size - 1.0D) / (maxFrames - 1.0D);
        int lastIndex = -1;
        for (int i = 0; i < maxFrames; i++) {
            int index = (i == maxFrames - 1) ? size - 1 : (int) Math.round(i * step);
            if (index == lastIndex) {
                continue;
            }
            lastIndex = index;
            sampled.add(frames.get(index));
        }
        return sampled;
    }
}