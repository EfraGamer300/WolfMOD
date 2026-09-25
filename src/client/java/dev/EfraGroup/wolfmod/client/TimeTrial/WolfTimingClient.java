package dev.EfraGroup.wolfmod.client.TimeTrial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;

public final class WolfTimingClient {
    public static final String CHANNEL = "wolfnetwork:settings";
    public static final int PROTOCOL_VERSION = 1;

    public static final String HELLO = "tt_v1_hello";
    public static final String CAPABILITIES = "tt_v1_capabilities";
    public static final String READY = "tt_v1_ready";
    public static final String GEOMETRY = "tt_v1_geometry";
    public static final String PING = "tt_v1_ping";
    public static final String PONG = "tt_v1_pong";
    public static final String RESULT = "tt_v1_result";
    public static final String DISARM = "tt_v1_disarm";
    public static final String REPORT = "tt_v1_report";

    private static final long PING_INTERVAL_NANOS = 5_000_000_000L;
    private static final long PING_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int MAX_PING_RETRIES = 3;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final SweptRegionDetector detector = new SweptRegionDetector();
    private PacketSender sender;
    private boolean connected;
    private boolean helloSent;
    private boolean capabilitiesSent;
    private boolean clientReadySent;
    private boolean serverReady;
    private boolean geometryInstalled;
    private boolean ready;
    private String runId = "local";
    private long sequence;
    private long pingId;
    private long pingSentAtNanos;
    private long nextPingAtNanos;
    private int pingRetries;
    private int pongDelayMillis = -1;
    private long localStartNanos;
    private String lastResultRunId;
    private Long lastResultDisplayMillis;

    public static boolean isTimingKey(String key) {
        return key != null && key.startsWith("tt_v1_");
    }

    public void handle(String key, String value, PacketSender responseSender) {
        if (responseSender != null) {
            sender = responseSender;
        }
        if (!isTimingKey(key)) {
            return;
        }
        JsonObject object = parseObject(value);
        if (object == null) {
            return;
        }
        String messageKey = key.trim();
        if ("tt_v1_message".equals(messageKey)) {
            messageKey = inferKey(object);
        }
        if (messageKey == null) {
            return;
        }
        switch (messageKey) {
            case HELLO -> onHello(object);
            case CAPABILITIES -> onCapabilities(object);
            case READY -> onReady(object);
            case GEOMETRY -> onGeometry(object);
            case PING -> onPing(object);
            case PONG -> onPong(object);
            case REPORT -> {
            }
            case RESULT -> onResult(object);
            case DISARM -> onDisarm();
            default -> {
            }
        }
    }

    public void handle(WolfConfigPayload payload, PacketSender responseSender) {
        if (payload != null) {
            handle(payload.key(), payload.value(), responseSender);
        }
    }

    public void onJoin(MinecraftClient client, PacketSender packetSender) {
        if (packetSender != null) {
            sender = packetSender;
        }
        connected = true;
        resetNegotiation();
        sendHello();
        sendCapabilities();
    }

    public void onJoin(PacketSender packetSender) {
        onJoin(MinecraftClient.getInstance(), packetSender);
    }

    public void onDisconnect() {
        connected = false;
        sender = null;
        resetNegotiation();
        detector.disarm();
        Timer.reset();
    }

    public void tick(MinecraftClient client) {
        if (
            client == null
                || !connected
                || client.world == null
                || client.player == null
                || client.getNetworkHandler() == null
        ) {
            return;
        }

        long now = System.nanoTime();
        if (client.isPaused()) {
            Timer.pauseAtNanos(now);
            detector.tick(client, now);
            return;
        }
        Timer.resumeAtNanos(now);

        if (!helloSent) {
            sendHello();
        }
        if (!capabilitiesSent) {
            sendCapabilities();
        }
        if (serverReady && !clientReadySent) {
            sendReady();
        }
        maybeSendPing(now);
        if (!ready) {
            detector.resetTracking();
            return;
        }

        SweptRegionDetector.TickResult result = detector.tick(client, now);
        handleDetectorStatus(result.status());
        for (SweptRegionDetector.Crossing crossing : result.crossings()) {
            handleLocalCrossing(crossing);
        }
        Timer.update();
    }

    public boolean ownsTimer() {
        return ready;
    }

    public void resetAfterLegacyTimer(boolean running) {
        if (ready) {
            detector.invalidateRun();
            localStartNanos = 0L;
        }
        if (running) {
            Timer.start();
        } else {
            Timer.stop();
        }
    }

    public SweptRegionDetector detector() {
        return detector;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean hasTimingGeometry() {
        return geometryInstalled;
    }

    public String runId() {
        return runId;
    }

    public long sequence() {
        return sequence;
    }

    public int pongDelayMillis() {
        return pongDelayMillis;
    }

    public String lastResultRunId() {
        return lastResultRunId;
    }

    public Long lastResultDisplayMillis() {
        return lastResultDisplayMillis;
    }

    private void resetNegotiation() {
        helloSent = false;
        capabilitiesSent = false;
        clientReadySent = false;
        serverReady = false;
        geometryInstalled = false;
        ready = false;
        detector.disarm();
        sequence = 0L;
        runId = "local";
        localStartNanos = 0L;
        lastResultRunId = null;
        lastResultDisplayMillis = null;
        pongDelayMillis = -1;
        pingSentAtNanos = 0L;
        nextPingAtNanos = 0L;
        pingRetries = 0;
        pingId = 0L;
    }

    private void onHello(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        capabilitiesSent = false;
        clientReadySent = false;
        serverReady = false;
        geometryInstalled = false;
        ready = false;
        detector.disarm();
        Timer.reset();
        sendCapabilities();
    }

    private void onCapabilities(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        if (object.has("rttMillis") || object.has("pingMillis")) {
            pongDelayMillis = integer(object, "rttMillis", integer(object, "pingMillis", pongDelayMillis));
        }
        updateReady();
    }

    private void onReady(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        if (
            !booleanValue(object, "enabled", true)
                || !booleanValue(object, "accepted", true)
                || !booleanValue(object, "ready", true)
        ) {
            onDisarm();
            return;
        }
        serverReady = true;
        String embeddedGeometry = text(object, "geometry");
        if (embeddedGeometry != null) {
            installGeometry(embeddedGeometry);
        }
        updateReady();
    }

    private void onGeometry(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        String geometryJson = text(object, "geometry");
        if (geometryJson == null) {
            geometryJson = text(object, "json");
        }
        if (geometryJson == null) {
            geometryJson = GSON.toJson(object);
        }
        installGeometry(geometryJson);
        if (booleanValue(object, "ready", false)) {
            serverReady = true;
        }
        updateReady();
    }

    private void onPing(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        long sequence = longValue(object, "sequence", longValue(object, "pingId", -1L));
        if (sequence < 0L) {
            return;
        }
        JsonObject pong = baseObject();
        pong.addProperty("sequence", sequence);
        send(PONG, pong);
    }

    private void onPong(JsonObject object) {
        if (pingSentAtNanos == 0L) {
            return;
        }
        long sequence = longValue(object, "sequence", longValue(object, "pingId", -1L));
        if (sequence != pingId) {
            return;
        }
        long delay = Math.max(0L, System.nanoTime() - pingSentAtNanos);
        pongDelayMillis = (int) Math.min(Integer.MAX_VALUE, delay / 1_000_000L);
        pingSentAtNanos = 0L;
        pingRetries = 0;
        nextPingAtNanos = System.nanoTime() + PING_INTERVAL_NANOS;
    }

    private void onResult(JsonObject object) {
        if (protocol(object) != PROTOCOL_VERSION) {
            return;
        }
        lastResultRunId = nonBlank(text(object, "runId"), runId);
        long display = longValue(object, "displayMillis", longValue(object, "finalMillis", -1L));
        if (display >= 0L) {
            lastResultDisplayMillis = display;
        }
    }

    private void onDisarm() {
        clientReadySent = false;
        serverReady = false;
        ready = false;
        detector.resetRun();
        sequence = 0L;
        localStartNanos = 0L;
        Timer.reset();
    }

    private void handleDetectorStatus(SweptRegionDetector.Status status) {
        switch (status) {
            case NOT_DRIVING, WORLD_CHANGED, TELEPORT, DISCONNECTED -> {
                detector.invalidateRun();
                localStartNanos = 0L;
                Timer.reset();
            }
            default -> {
            }
        }
    }

    private void updateReady() {
        ready = serverReady && capabilitiesSent && clientReadySent && geometryInstalled;
    }

    private void installGeometry(String json) {
        try {
            TimingGeometry geometry = TimingGeometry.fromJson(unwrapJsonString(json));
            String nextRunId = geometry.geometryId();
            if (nextRunId == null || nextRunId.isBlank()) {
                return;
            }
            runId = nextRunId;
            sequence = 0L;
            localStartNanos = 0L;
            detector.setGeometry(geometry);
            geometryInstalled = true;
            clientReadySent = false;
        } catch (RuntimeException ignored) {
            geometryInstalled = false;
        }
        updateReady();
    }

    private void handleLocalCrossing(SweptRegionDetector.Crossing crossing) {
        TimingRegion.Role role = crossing.region().role();
        long atNanos = crossing.atNanos();
        if (role == TimingRegion.Role.START) {
            localStartNanos = atNanos;
            Timer.startAtNanos(atNanos);
            sendReport(role, atNanos, atNanos, 0L, crossing.region().id());
        } else if (role == TimingRegion.Role.END) {
            long startNanos = localStartNanos > 0L ? localStartNanos : Timer.getStartAtNanos();
            Timer.stopAtNanos(atNanos);
            long displayMillis = startNanos > 0L
                ? Math.max(0L, atNanos - startNanos) / 1_000_000L
                : Timer.getElapsedMillis();
            sendReport(role, startNanos, atNanos, displayMillis, crossing.region().id());
            detector.resetRun();
        }
    }

    private void sendHello() {
        if (!canSend() || helloSent) {
            return;
        }
        JsonObject hello = baseObject();
        hello.addProperty("client", "wolfmod");
        hello.addProperty("version", PROTOCOL_VERSION);
        if (send(HELLO, hello)) {
            helloSent = true;
        }
    }

    private void sendCapabilities() {
        if (!canSend() || capabilitiesSent) {
            return;
        }
        JsonObject capabilities = baseObject();
        capabilities.addProperty("client", "wolfmod");
        capabilities.addProperty("subtick", true);
        capabilities.addProperty("aabb", true);
        capabilities.addProperty("poly", true);
        capabilities.addProperty("json", true);
        capabilities.addProperty("vehicle", "AbstractBoatEntity");
        com.google.gson.JsonArray clientCapabilities = new com.google.gson.JsonArray();
        clientCapabilities.add("TT_CLIENT_REPORT_V1");
        clientCapabilities.add("TT_SWEPT_DETECT_V1");
        capabilities.add("clientCapabilities", clientCapabilities);
        if (send(CAPABILITIES, capabilities)) {
            capabilitiesSent = true;
        }
    }

    private void sendReady() {
        if (!canSend() || clientReadySent) {
            return;
        }
        JsonObject message = baseObject();
        message.addProperty("client", "wolfmod");
        message.addProperty("subtick", true);
        message.addProperty("runId", runId);
        if (send(READY, message)) {
            clientReadySent = true;
        }
        updateReady();
    }

    private void maybeSendPing(long now) {
        if (pingSentAtNanos != 0L && now - pingSentAtNanos >= PING_TIMEOUT_NANOS) {
            pingSentAtNanos = 0L;
            pingRetries++;
        }
        if (!canSend() || pingSentAtNanos != 0L || now < nextPingAtNanos || pingRetries >= MAX_PING_RETRIES) {
            return;
        }
        pingId++;
        pingSentAtNanos = now;
        pingRetries++;
        JsonObject ping = baseObject();
        ping.addProperty("sequence", pingId);
        ping.addProperty("clientTimeNanos", now);
        if (!send(PING, ping)) {
            pingSentAtNanos = 0L;
            pingRetries--;
        }
    }

    private void sendReport(TimingRegion.Role role, long startNanos, long atNanos, long displayMillis, String regionId) {
        if (!canSend() || startNanos <= 0L || atNanos <= 0L) {
            return;
        }
        JsonObject report = baseObject();
        report.addProperty("runId", runId);
        report.addProperty("role", role.name());
        report.addProperty("sequence", sequence);
        report.addProperty("startNanos", startNanos);
        report.addProperty("atNanos", atNanos);
        report.addProperty("displayMillis", Math.max(0L, displayMillis));
        report.addProperty("regionId", regionId == null ? "" : regionId);
        report.addProperty("proposal", true);
        if (send(REPORT, report)) {
            sequence++;
        }
    }

    private boolean send(String key, JsonObject value) {
        if (!canSend()) {
            return false;
        }
        try {
            sender.sendPacket(new WolfConfigPayload(key, GSON.toJson(value)));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canSend() {
        if (!connected || sender == null) {
            return false;
        }
        try {
            return ClientPlayNetworking.canSend(WolfConfigPayload.ID);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static JsonObject baseObject() {
        JsonObject object = new JsonObject();
        object.addProperty("protocol", PROTOCOL_VERSION);
        return object;
    }

    private static JsonObject parseObject(String value) {
        if (value == null || value.isBlank() || value.length() > 32767) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            return object.has("protocol") && integer(object, "protocol", -1) == PROTOCOL_VERSION
                ? object
                : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String unwrapJsonString(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
                return parsed.getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return value;
    }

    private static int protocol(JsonObject object) {
        return integer(object, "protocol", -1);
    }

    private static String inferKey(JsonObject object) {
        if (object.has("geometry") || object.has("regions")) {
            return GEOMETRY;
        }
        if (object.has("serverTimeNanos")) {
            return PING;
        }
        if (object.has("clientTimeNanos") || object.has("rttMillis")) {
            return PONG;
        }
        if (object.has("runId") && (object.has("displayMillis") || object.has("officialMillis"))) {
            return RESULT;
        }
        if (object.has("accepted") || object.has("enabled") || object.has("ready")) {
            return READY;
        }
        if (object.has("disarm") || object.has("disarmed")) {
            return DISARM;
        }
        return null;
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static int integer(JsonObject object, String field, int fallback) {
        try {
            JsonElement value = object.get(field);
            return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String field, long fallback) {
        try {
            JsonElement value = object.get(field);
            return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String field, boolean fallback) {
        try {
            JsonElement value = object.get(field);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                return fallback;
            }
            if (value.getAsJsonPrimitive().isBoolean()) {
                return value.getAsBoolean();
            }
            String valueText = value.getAsString().trim().toLowerCase(Locale.ROOT);
            return "true".equals(valueText) || "1".equals(valueText) || "yes".equals(valueText);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
