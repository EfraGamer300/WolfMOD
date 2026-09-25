package dev.EfraGroup.wolfmod.client.radio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class NimApiClient {

    private static final String API_URL = "https://api.kilo.ai/api/gateway/chat/completions";
    private static final String MODEL = "kilo-auto/free";

    private static String lastError = "";

    public static String getLastError() {
        return lastError;
    }

    public static CompletableFuture<JsonObject> processAudio(Path wavPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String transcription = LocalTranscriber.transcribe(wavPath);

                if (transcription == null || transcription.trim().isEmpty()) {
                    return errorJson("Transcrição vazia (não foi possível entender o áudio)");
                }

                String reply = queryKilo(transcription.trim());
                if (reply == null || reply.isEmpty()) {
                    String detail = lastError.isEmpty() ? "Tente de novo." : lastError;
                    return errorJson("IA não respondeu: " + detail);
                }

                return command(true, "IA", reply, "");
            } catch (Exception e) {
                lastError = e.getMessage();
                return errorJson("Erro: " + e.getMessage());
            }
        });
    }

    private static String apiKey() {
        try {
            Path keyPath = Path.of("config", "f1_radio", "kilo_key.txt");
            if (!Files.exists(keyPath)) {
                return "";
            }
            return Files.readString(keyPath).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String queryKilo(String transcription) {
        try {
            String key = apiKey();

            JsonObject req = new JsonObject();
            req.addProperty("model", MODEL);
            req.addProperty("max_tokens", 120);
            req.addProperty("temperature", 0.7);

            JsonArray messages = new JsonArray();

            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content",
                    "Voce e o engenheiro de radio de um piloto de Formula 1. "
                            + "Responda em portugues, curto e direto (maximo 2 frases), "
                            + "como se estivesse falando no radio da equipe.");
            messages.add(sys);

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", transcription);
            messages.add(user);

            req.add("messages", messages);

            HttpClient http = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                    .timeout(Duration.ofSeconds(30));
            if (!key.isEmpty()) {
                builder.header("Authorization", "Bearer " + key);
            }
            HttpRequest request = builder.build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                lastError = "HTTP " + response.statusCode();
                String body = response.body() == null ? "" : response.body();
                System.out.println("[RadioDebug] Kilo HTTP " + response.statusCode()
                        + " body: " + body.substring(0, Math.min(300, body.length())));
                return null;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("choices")) {
                lastError = "resposta sem choices";
                return null;
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() == 0) {
                lastError = "choices vazio";
                return null;
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (!message.has("content") || message.get("content").isJsonNull()) {
                lastError = "content vazio";
                return null;
            }
            String text = message.get("content").getAsString().trim();
            if (text.isEmpty()) {
                lastError = "resposta vazia";
                return null;
            }
            return text;
        } catch (Exception e) {
            lastError = e.getMessage();
            System.out.println("[RadioDebug] Kilo erro: " + e.getMessage());
            return null;
        }
    }

    private static JsonObject command(boolean valido, String tipo, String valor, String audio) {
        JsonObject obj = new JsonObject();
        obj.addProperty("comando_valido", valido);
        obj.addProperty("tipo_acao", tipo);
        obj.addProperty("valor_acao", valor);
        obj.addProperty("audio_local", audio);
        return obj;
    }

    private static JsonObject errorJson(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("comando_valido", false);
        err.addProperty("tipo_acao", "ERRO");
        err.addProperty("valor_acao", message);
        err.addProperty("audio_local", "");
        return err;
    }
}
