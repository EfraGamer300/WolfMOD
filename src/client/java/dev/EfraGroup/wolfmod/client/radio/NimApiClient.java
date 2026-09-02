package dev.EfraGroup.wolfmod.client.radio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class NimApiClient {

    public static CompletableFuture<JsonObject> processAudio(Path wavPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String transcription = LocalTranscriber.transcribe(wavPath);

                if (transcription.isEmpty()) {
                    return errorJson("Transcrição vazia (não foi possível entender o áudio)");
                }

                return localExtractCommand(transcription);
            } catch (Exception e) {
                return errorJson("Erro: " + e.getMessage());
            }
        });
    }

    private static JsonObject localExtractCommand(String transcription) {
        String lower = transcription.toLowerCase().trim();

        // --- SELECAO_PNEU via Pollinations (function calling) ---
        // Encaminha apenas pedidos com contexto de pneu para a IA.
        if (hasPneuContext(lower)) {
            JsonObject poli = interpretTireCommand(transcription);
            if (poli != null) {
                return poli;
            }
        }

        // --- SELECAO_PNEU (Tire selection, fallback local) ---
        // Check by tire type keywords first
        if (containsAny(lower, "duro", "hard")) {
            return command(true, "SELECAO_PNEU", "HARD", "box_hard.wav");
        }
        if (containsAny(lower, "médio", "medio", "medium")) {
            return command(true, "SELECAO_PNEU", "MEDIUM", "box_medium.wav");
        }
        if (containsAny(lower, "macio", "soft", "macia", "softe")) {
            return command(true, "SELECAO_PNEU", "SOFT", "box_soft.wav");
        }
        if (containsAny(lower, "intermediário", "intermediario", "intermediate", "chuva leve")) {
            return command(true, "SELECAO_PNEU", "INTERMEDIATE", "box.wav");
        }
        if (containsAny(lower, "chuva", "molhado", "wet", "pneu de chuva")) {
            return command(true, "SELECAO_PNEU", "WET", "box.wav");
        }
        // Contextual: "escolha/quero/preciso/vou + pneu" without specific type → ask or default MEDIUM
        if (hasPneuContext(lower) && !containsAny(lower, "duro", "hard", "médio", "medio", "medium", "macio", "soft", "macia")) {
            return command(true, "SELECAO_PNEU", "MEDIUM", "box_medium.wav");
        }

        // --- PARADA_BOX (Pit stop) ---
        if (containsAny(lower, "box", "pit", "parada", "entrar")) {
            if (containsAny(lower, "próxima", "proxima", "próximo", "proximo", "volta", "depois")) {
                return command(true, "PARADA_BOX", "NA_PROXIMA", "box.wav");
            }
            return command(true, "PARADA_BOX", "BOX", "box.wav");
        }

        // --- ESTRATEGIA (Strategy) ---
        if (containsAny(lower, "agressivo", "agressiva", "atacar")) {
            return command(true, "ESTRATEGIA", "AGRESSIVO", "undercut.wav");
        }
        if (containsAny(lower, "conservador", "conservadora", "seguro", "manter")) {
            return command(true, "ESTRATEGIA", "CONSERVADOR", "stay_out.wav");
        }
        if (containsAny(lower, "undercut")) {
            return command(true, "ESTRATEGIA", "UNDERCUT", "undercut.wav");
        }
        if (containsAny(lower, "overcut")) {
            return command(true, "ESTRATEGIA", "OVERCUT", "overcut.wav");
        }

        // --- CONFIRMACAO (Confirmation) ---
        if (containsAny(lower, "sim", "ok", "confirmado", "afirmativo", "pode ser")) {
            return command(true, "CONFIRMACAO", "SIM", "confirm.wav");
        }
        if (containsAny(lower, "não", "nao", "negativo", "nada")) {
            return command(true, "CONFIRMACAO", "NAO", "negative.wav");
        }
        if (containsAny(lower, "entendido", "entendi", "ciente", "recebido", "cópia", "copia")) {
            return command(true, "CONFIRMACAO", "ENTENDIDO", "confirm.wav");
        }

        // --- RELATO (Status report) ---
        if (containsAny(lower, "asa", "wing", "dano", "quebrou", "quebrada")) {
            if (containsAny(lower, "frente", "dianteira", "frontal")) {
                return command(true, "RELATO", "DANO_ASA", "front_wing.wav");
            }
            return command(true, "RELATO", "DANO_ASA", "rear_wing.wav");
        }
        if (containsAny(lower, "pneu", "pneus", "vibração", "vibracao", "degradação", "degradacao")) {
            if (containsAny(lower, "problema", "degrad", "acabando", "velho", "gasto")) {
                return command(true, "RELATO", "PROBLEMA_PNEU", "box.wav");
            }
        }
        if (containsAny(lower, "motor", "engine", "mecânico", "mecanico")) {
            if (containsAny(lower, "ok", "bem", "bom", "normal", "funcionando")) {
                return command(true, "RELATO", "MOTOR_OK", "engine_ok.wav");
            }
            if (containsAny(lower, "problema", "falha", "quebrou", "quebrado", "fumaça", "fumaca")) {
                return command(true, "RELATO", "PROBLEMA_PNEU", "box.wav");
            }
        }
        if (containsAny(lower, "bandeira", "flag", "amarela", "vermelha", "safety car", "sc")) {
            return command(true, "RELATO", "BANDEIRA", "radio_check.wav");
        }

        // --- ERS / DRS ---
        if (containsAny(lower, "drs")) {
            if (containsAny(lower, "abrir", "abre", "aberto", "ativa", "ativar")) {
                return command(true, "CONFIRMACAO", "SIM", "drs_open.wav");
            }
            return command(true, "CONFIRMACAO", "SIM", "drs_closed.wav");
        }

        // --- Default: no match ---
        return errorJson("Não foi possível identificar o comando: \"" + transcription + "\"");
    }

    private static JsonObject interpretTireCommand(String transcription) {
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://gen.pollinations.ai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildTireRequest(transcription)))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("choices")) {
                return null;
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() == 0) {
                return null;
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (!message.has("tool_calls") || message.get("tool_calls").isJsonNull()) {
                return null;
            }
            JsonArray toolCalls = message.getAsJsonArray("tool_calls");
            for (int i = 0; i < toolCalls.size(); i++) {
                JsonObject fn = toolCalls.get(i).getAsJsonObject().getAsJsonObject("function");
                if (!"change_tires".equals(fn.get("name").getAsString())) {
                    continue;
                }
                JsonObject args = JsonParser.parseString(fn.get("arguments").getAsString()).getAsJsonObject();
                String compound = args.has("compound")
                        ? args.get("compound").getAsString().toUpperCase()
                        : "MEDIUM";
                return command(true, "SELECAO_PNEU", compound, audioForCompound(compound));
            }
            return null;
        } catch (Exception e) {
            System.out.println("[RadioDebug] Pollinations erro: " + e.getMessage());
            return null;
        }
    }

    private static String buildTireRequest(String transcription) {
        JsonObject req = new JsonObject();
        req.addProperty("model", "openai");

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content",
                "You are a Formula racing engineer. When the driver asks to change tires or pick a "
                        + "compound for the next pit stop, call the change_tires tool. Map Portuguese to "
                        + "compounds: macio/soft -> SOFT, medio/medium -> MEDIUM, duro/hard -> HARD, "
                        + "intermediario/intermediate -> INTERMEDIATE, chuva/molhado/wet -> WET.");
        messages.add(sys);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", transcription);
        messages.add(user);
        req.add("messages", messages);

        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        JsonObject fn = new JsonObject();
        fn.addProperty("name", "change_tires");
        fn.addProperty("description", "Select the tire compound for the next pit stop");
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject compound = new JsonObject();
        compound.addProperty("type", "string");
        JsonArray enums = new JsonArray();
        enums.add("SOFT");
        enums.add("MEDIUM");
        enums.add("HARD");
        enums.add("INTERMEDIATE");
        enums.add("WET");
        compound.add("enum", enums);
        props.add("compound", compound);
        params.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("compound");
        params.add("required", required);
        fn.add("parameters", params);
        tool.add("function", fn);
        tools.add(tool);
        req.add("tools", tools);

        return req.toString();
    }

    private static String audioForCompound(String compound) {
        return switch (compound) {
            case "SOFT" -> "box_soft.wav";
            case "MEDIUM" -> "box_medium.wav";
            case "HARD" -> "box_hard.wav";
            default -> "box.wav";
        };
    }

    private static boolean hasPneuContext(String lower) {
        String[] tireContextWords = {"pneu", "pneus", "escolha", "escolhas", "escolher",
                "trocar", "colocar", "quero", "quere", "preciso", "vou", "vai",
                "bora", "vamos", "muda", "mudar", "trocá", "trocando"};
        for (String w : tireContextWords) {
            if (lower.contains(w)) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
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
