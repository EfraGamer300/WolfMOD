package dev.EfraGroup.wolfplugin.content;

// Protocolo WolfContent Fase 1. Canal wolfnetwork:settings, codec VarInt key+value.
// Sem Gson no wolfplugin: JSON montado/parseado na mao com escape minimo.
public final class ContentProtocol {
    public static final int PROTOCOL_VERSION = 1;
    // Server -> client: oferta de conteudo + versoes + manifest.
    public static final String HELLO = "wc_v1_hello";
    // Client -> server: inventario do mod (versao, loader, mc).
    public static final String STATUS = "wc_v1_status";
    // Reservado p/ Fase 4 (prompt download/update). Nao usar ainda.
    public static final String PROMPT = "wc_v1_prompt";

    private ContentProtocol() {
    }

    // {"protocol":1,"requiredVersion":"..","minVersion":"..","manifestUrl":"..","serverId":".."}
    public static String buildHello(String required, String min, String manifestUrl, String serverId) {
        return "{\"protocol\":" + PROTOCOL_VERSION
                + ",\"requiredVersion\":\"" + escapeJson(required) + "\""
                + ",\"minVersion\":\"" + escapeJson(min) + "\""
                + ",\"manifestUrl\":\"" + escapeJson(manifestUrl) + "\""
                + ",\"serverId\":\"" + escapeJson(serverId) + "\"}";
    }

    public static boolean isContentKey(String key) {
        return key != null && (key.startsWith("wc_v1_") || key.startsWith("content_"));
    }

    public static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    // Extrai valor de JSON plano: "key":"str" (unescape basico) ou "key":123/true.
    public static String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String quoted = "\"" + key + "\"";
        int idx = json.indexOf(quoted);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + quoted.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char ch = json.charAt(i);
                if (ch == '\\' && i + 1 < json.length()) {
                    char nx = json.charAt(i + 1);
                    if (nx == 'u' && i + 5 < json.length()) {
                        try {
                            sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                        } catch (NumberFormatException e) {
                            sb.append(nx);
                        }
                        i += 6;
                    } else {
                        if (nx == 'n') {
                            sb.append('\n');
                        } else if (nx == 'r') {
                            sb.append('\r');
                        } else if (nx == 't') {
                            sb.append('\t');
                        } else {
                            sb.append(nx);
                        }
                        i += 2;
                    }
                } else if (ch == '"') {
                    return sb.toString();
                } else {
                    sb.append(ch);
                    i++;
                }
            }
            return null;
        }
        int j = i;
        while (j < json.length() && json.charAt(j) != ',' && json.charAt(j) != '}') {
            j++;
        }
        String raw = json.substring(i, j).trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        return raw;
    }
}
