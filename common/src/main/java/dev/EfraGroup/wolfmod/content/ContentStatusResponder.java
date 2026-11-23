package dev.EfraGroup.wolfmod.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * WolfContent Fase 1 — protocolo client-side (responder).
 *
 * <p>Chaves (espelham o servidor WolfPlugin):</p>
 * <ul>
 *   <li>{@code wc_v1_hello} S-&gt;C: {protocol, requiredVersion, minVersion, manifestUrl, serverId}</li>
 *   <li>{@code wc_v1_status} C-&gt;S: {protocol, installedVersion, loader, gameVersion, modVersion}</li>
 * </ul>
 * <p>Fase 1: {@code installedVersion} e sempre "0" (sem pacote ainda — Fase 4 traz o dado real).
 * So deteccao + aviso, sem download.</p>
 */
public final class ContentStatusResponder {
    public static final int PROTOCOL = 1;
    public static final String HELLO = "wc_v1_hello";
    public static final String STATUS = "wc_v1_status";

    /** Versao do pacote de dados instalado. Fase 1: sempre "0". */
    private static volatile String installedVersion = "0";

    private ContentStatusResponder() {
    }

    public static boolean isContentKey(String key) {
        return HELLO.equals(key);
    }

    public static void handle(WolfConfigPayload payload, WolfResponder responder) {
        JsonObject hello = parse(payload.value());
        if (hello == null || intValue(hello, "protocol", -1) != PROTOCOL) {
            return;
        }

        // Valida o serverId ANTES de acordar o gate: outro plugin pode usar o
        // mesmo prefixo wc_v1_ e nao deve ativar o conteudo da WolfNetwork.
        String serverId = str(hello, "serverId", "");
        if (!"wolfnetwork".equals(serverId)) {
            return;
        }
        ContentGate.markHelloReceived();

        String required = str(hello, "requiredVersion", "0");

        if (responder != null) {
            responder.sendConfig(new WolfConfigPayload(STATUS, statusJson()));
        }

        if (compare(required, installedVersion) > 0) {
            ContentPromptToast.show(required, installedVersion);
        }
    }

    private static String statusJson() {
        JsonObject json = new JsonObject();
        json.addProperty("protocol", PROTOCOL);
        json.addProperty("installedVersion", installedVersion);
        json.addProperty("loader", detectLoader());
        String game = "1.21.4";
        try {
            game = MinecraftClient.getInstance().getGameVersion();
        } catch (Exception ignored) {
        }
        json.addProperty("gameVersion", game);
        json.addProperty("modVersion", ContentModVersion.CURRENT);
        return json.toString();
    }

    private static JsonObject parse(String value) {
        if (value == null || value.isBlank() || value.length() > 32767) {
            return null;
        }
        try {
            return JsonParser.parseString(value).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject json, String key, String fallback) {
        try {
            return json.has(key) ? json.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Descobre o loader sem importar API de loader nenhuma no :common.
     *
     * <p>Quilt fica primeiro porque ele carrega a API do Fabric Loader junto;
     * ja NeoForge e Forge tem classes de entrada distintas.</p>
     */
    private static String detectLoader() {
        if (classPresent("org.quiltmc.loader.api.QuiltLoader")) {
            return "quilt";
        }
        if (classPresent("net.neoforged.fml.ModList")) {
            return "neoforge";
        }
        if (classPresent("net.minecraftforge.fml.ModList")) {
            return "forge";
        }
        if (classPresent("net.fabricmc.loader.api.FabricLoader")) {
            return "fabric";
        }
        return "unknown";
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, ContentStatusResponder.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Comparacao numerica (nao lexicografica): "9" < "12". */
    static int compare(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a.trim()), Integer.parseInt(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    /** Permite a Fase 4 injetar a versao real do pacote instalado. */
    public static void setInstalledVersion(String version) {
        if (version != null && !version.isBlank()) {
            installedVersion = version.trim();
        }
    }
}
