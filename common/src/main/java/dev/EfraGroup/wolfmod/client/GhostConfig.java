package dev.EfraGroup.wolfmod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side settings for the ghost visuals.
 *
 * <p>Kept separate from the radio settings: this is about how the personal best is drawn, and
 * a corrupt or missing file here must not stop the timing protocol from working.
 */
public final class GhostConfig {

    /** Default ARGB, matching the translucent purple used by the ghost boat. */
    private static final int DEFAULT_LINE_COLOR = 0xFFB07CE8;

    private static final Path CONFIG_PATH =
            Path.of("config", "wolfmod", "ghost.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Draw the personal best trajectory on the ground. */
    public boolean renderLine = true;

    /** ARGB colour of that trajectory. */
    public int lineColor = DEFAULT_LINE_COLOR;

    /** Boat shown for the ghost; other boat types are selectable from config. */
    public String ghostBoatType = "oak";

    private static GhostConfig instance;

    private GhostConfig() {
    }

    public static synchronized GhostConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Re-reads the file, used by the config screen after an edit. */
    public static synchronized void reload() {
        instance = load();
    }

    private static GhostConfig load() {
        try {
            Path path = configPath();
            if (path != null && Files.isRegularFile(path)) {
                GhostConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), GhostConfig.class);
                if (loaded != null) {
                    loaded.validate();
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall through to defaults; a bad config must not break ghost rendering.
        }
        GhostConfig fallback = new GhostConfig();
        fallback.validate();
        return fallback;
    }

    /** Writes the current values back to disk. */
    public synchronized void save() {
        Path path = configPath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // Non-fatal: the in-memory values still apply for this session.
        }
    }

    public void validate() {
        this.lineColor &= 0xFFFFFFFF;
        if (this.ghostBoatType == null || this.ghostBoatType.isBlank()) {
            this.ghostBoatType = "oak";
        }
    }

    private static Path configPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return null;
        }
        return client.runDirectory.toPath().resolve(CONFIG_PATH);
    }
}