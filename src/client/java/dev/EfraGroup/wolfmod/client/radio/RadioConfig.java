package dev.EfraGroup.wolfmod.client.radio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class RadioConfig {
    private static final Path CONFIG_PATH = Path.of("config", "f1_radio", "config.json");

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            saveDefault();
        }
    }

    private static void saveDefault() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("tipo", "local");
            Files.writeString(CONFIG_PATH, obj.toString());
        } catch (Exception e) {
            System.out.println("[RadioDebug] ERRO ao salvar config.json: " + e.getMessage());
        }
    }
}
