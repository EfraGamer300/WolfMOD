package dev.EfraGroup.wolfmod.client.radio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

public class RadioSettings {
    private static final Path SETTINGS_PATH = Path.of("config", "f1_radio", "settings.json");

    private static double speechThreshold = 80.0;
    private static String selectedMixerName = "";
    private static boolean noiseSuppression = true;
    private static boolean sidetoneEnabled = false;
    private static double sidetoneVolume = 0.8;

    public static void load() {
        try {
            if (!Files.exists(SETTINGS_PATH)) {
                save();
                return;
            }
            String content = Files.readString(SETTINGS_PATH);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("speechThreshold")) {
                speechThreshold = obj.get("speechThreshold").getAsDouble();
            }
            if (obj.has("selectedMixer")) {
                selectedMixerName = obj.get("selectedMixer").getAsString();
            }
            if (obj.has("noiseSuppression")) {
                noiseSuppression = obj.get("noiseSuppression").getAsBoolean();
            }
            if (obj.has("sidetoneEnabled")) {
                sidetoneEnabled = obj.get("sidetoneEnabled").getAsBoolean();
            }
            if (obj.has("sidetoneVolume")) {
                sidetoneVolume = obj.get("sidetoneVolume").getAsDouble();
            }
        } catch (Exception e) {
            System.out.println("[RadioDebug] ERRO ao ler settings: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("speechThreshold", speechThreshold);
            obj.addProperty("selectedMixer", selectedMixerName);
            obj.addProperty("noiseSuppression", noiseSuppression);
            obj.addProperty("sidetoneEnabled", sidetoneEnabled);
            obj.addProperty("sidetoneVolume", sidetoneVolume);
            Files.writeString(SETTINGS_PATH, obj.toString());
        } catch (Exception e) {
            System.out.println("[RadioDebug] ERRO ao salvar settings: " + e.getMessage());
        }
    }

    public static double getThreshold() {
        return speechThreshold;
    }

    public static void setThreshold(double value) {
        speechThreshold = Math.max(1.0, Math.min(1000.0, value));
        save();
    }

    public static String getSelectedMixer() {
        return selectedMixerName;
    }

    public static void setSelectedMixer(String name) {
        selectedMixerName = name;
        save();
    }

    public static boolean isNoiseSuppressionEnabled() {
        return noiseSuppression;
    }

    public static void setNoiseSuppressionEnabled(boolean enabled) {
        noiseSuppression = enabled;
        save();
    }

    public static boolean isSidetoneEnabled() {
        return sidetoneEnabled;
    }

    public static void setSidetoneEnabled(boolean enabled) {
        sidetoneEnabled = enabled;
        save();
    }

    public static double getSidetoneVolume() {
        return sidetoneVolume;
    }

    public static void setSidetoneVolume(double volume) {
        sidetoneVolume = Math.max(0.0, Math.min(1.0, volume));
        save();
    }
}
