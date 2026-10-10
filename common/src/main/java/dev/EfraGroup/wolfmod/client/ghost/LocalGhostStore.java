package dev.EfraGroup.wolfmod.client.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.MinecraftClient;

/**
 * On-disk cache of personal best laps, so a lap the player already drove is available on
 * the next visit without asking the server for the geometry again.
 *
 * <p>This is a pure cache: the server stays the source of truth and can overwrite or
 * invalidate any entry. It exists to remove the download from the critical path, not to
 * decide what the player's best lap is.
 */
public final class LocalGhostStore {

    /** Bumped when the on-disk shape changes so stale files are ignored, not misread. */
    private static final int FORMAT_VERSION = 1;

    /** Upper bound on frames kept per track, mirroring the server's payload cap. */
    private static final int MAX_FRAMES = 2000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LocalGhostStore() {
    }

    /**
     * Saves {@code data} for {@code trackId}, writing through a temp file so a crash mid-write
     * cannot leave a half-written entry that would fail to parse on the next launch.
     *
     * <p>Returns false when the path is unavailable (no config dir yet) or the write failed;
     * the caller keeps using the server copy in that case.
     */
    public static boolean save(String trackId, GhostData data) {
        if (trackId == null || trackId.isBlank() || data == null || data.frameCount() < 2) {
            return false;
        }
        Path target = fileFor(trackId);
        if (target == null) {
            return false;
        }
        Entry entry = new Entry(FORMAT_VERSION, trackId, data);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(entry, writer);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Nothing useful to do; the stale temp is harmless.
            }
            return false;
        }
    }

    /**
     * Loads the cached lap for {@code trackId}, or null when there is none or it cannot be
     * parsed. A malformed entry is treated as absent so a bad file degrades to "no local
     * ghost" rather than breaking ghost playback.
     */
    public static GhostData load(String trackId) {
        Path target = fileFor(trackId);
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            Entry entry = GSON.fromJson(reader, Entry.class);
            if (entry == null || entry.formatVersion != FORMAT_VERSION || entry.data == null) {
                return null;
            }
            GhostData data = entry.data;
            if (data.frameCount() < 2 || data.xs() == null || data.ticks() == null) {
                return null;
            }
            return data;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private static Path fileFor(String trackId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        Path gameDir = client.runDirectory.toPath();
        if (gameDir == null) {
            return null;
        }
        String safe = trackId.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        return gameDir.resolve("config").resolve("wolfmod").resolve("ghosts").resolve(safe + ".json");
    }

    /** Serialised wrapper so a future format change can be detected instead of misread. */
    private static final class Entry {
        int formatVersion;
        String trackId;
        GhostData data;

        Entry() {
        }

        Entry(int formatVersion, String trackId, GhostData data) {
            this.formatVersion = formatVersion;
            this.trackId = trackId;
            this.data = data;
        }
    }
}