package dev.EfraGroup.wolfmod.client.pack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

/**
 * Sincroniza o resource pack do Saphira sem prompt e sem reload em jogo.
 *
 * <p>Como funciona: o servidor (via WolfPlugin) manda {@code pack_manifest}
 * com {@code url|sha1}. O mod mantem um cache em {@code config/wolfmod} por
 * endereco de servidor, verifica updates em background no launch e aplica o
 * zip como pack global. Quando o cache bate com o vigente, responde
 * {@code pack_ready} e o Saphira pula o push vanilla.</p>
 *
 * <p>Tudo aqui e defensivo: qualquer falha cai para o fluxo vanilla normal
 * (o {@code pack_ready} so e enviado com o cache verificado E aplicado).</p>
 */
public final class PackSync {
    private static final Path PACKS_JSON = Path.of("config", "wolfmod", "packs.json");
    private static final String FILE_PREFIX = "wolfmod-saphira-";
    private static final long MAX_DOWNLOAD_BYTES = 32L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    private static final Gson GSON = new Gson();
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile boolean loaded;
    private static volatile boolean launchApplied;
    private static volatile String lastAddress = "";

    private PackSync() {
    }

    private static final class Entry {
        String manifestUrl;
        String zipUrl;
        String sha1;
        String file;
    }

    private static final class Store {
        Map<String, Entry> servers = new LinkedHashMap<>();
        String lastAddress = "";
    }

    /* ========================================================
     *  Persistencia
     * ======================================================== */

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.exists(PACKS_JSON)) {
                return;
            }
            Store store = GSON.fromJson(Files.readString(PACKS_JSON), Store.class);
            if (store != null) {
                if (store.servers != null) {
                    ENTRIES.putAll(store.servers);
                }
                if (store.lastAddress != null) {
                    lastAddress = store.lastAddress;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(PACKS_JSON.getParent());
            Store store = new Store();
            store.servers.putAll(ENTRIES);
            store.lastAddress = lastAddress;
            Files.writeString(PACKS_JSON, GSON.toJson(store));
        } catch (Exception ignored) {
        }
    }

    private static String safeName(String address) {
        String safe = address == null ? "server" : address.replaceAll("[^A-Za-z0-9.-]", "_");
        if (safe.isBlank()) {
            safe = "server";
        }
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return FILE_PREFIX + safe + ".zip";
    }

    /* ========================================================
     *  Launch: verifica updates em background
     * ======================================================== */

    /** Disparado no boot do client; so faz rede, nao toca no jogo. */
    public static void checkForUpdatesAsync() {
        Thread thread = new Thread(PackSync::checkForUpdates, "WolfMOD-PackSync");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkForUpdates() {
        try {
            load();
            List<Entry> snapshot;
            synchronized (PackSync.class) {
                snapshot = new ArrayList<>(ENTRIES.values());
            }
            boolean changed = false;
            for (Entry entry : snapshot) {
                if (entry == null || entry.manifestUrl == null || entry.manifestUrl.isBlank()) {
                    continue;
                }
                try {
                    Manifest manifest = fetchManifest(entry.manifestUrl);
                    if (manifest == null || manifest.sha1 == null) {
                        continue;
                    }
                    synchronized (PackSync.class) {
                        Entry current = findByManifest(entry.manifestUrl);
                        if (current == null || equalsHex(current.sha1, manifest.sha1)) {
                            continue;
                        }
                        if (manifest.url != null && !manifest.url.isBlank()) {
                            current.zipUrl = manifest.url;
                        }
                    }
                    Path tmp = downloadToTemp(entry.zipUrl, entry.file);
                    if (tmp == null) {
                        continue;
                    }
                    synchronized (PackSync.class) {
                        Entry current = findByManifest(entry.manifestUrl);
                        if (current == null || !entry.file.equals(current.file)) {
                            deleteQuietly(tmp);
                            continue;
                        }
                        if (!equalsHex(sha1HexOfFile(tmp), manifest.sha1)) {
                            deleteQuietly(tmp);
                            continue;
                        }
                        try {
                            Path target = Path.of("resourcepacks", current.file);
                            Files.createDirectories(target.getParent());
                            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception ignored) {
                            deleteQuietly(tmp);
                            continue;
                        }
                        current.sha1 = manifest.sha1.toLowerCase(java.util.Locale.ROOT);
                        changed = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (changed) {
                save();
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized Entry findByManifest(String manifestUrl) {
        for (Entry entry : ENTRIES.values()) {
            if (manifestUrl.equals(entry.manifestUrl)) {
                return entry;
            }
        }
        return null;
    }

    /** Uma vez por launch, na tela de titulo: ativa o pack do ultimo servidor. */
    public static void tickLaunchApply(MinecraftClient client) {
        if (launchApplied || client == null || client.world != null) {
            return;
        }
        launchApplied = true;
        try {
            load();
            String address;
            synchronized (PackSync.class) {
                address = lastAddress;
            }
            if (address == null || address.isBlank()) {
                return;
            }
            Entry entry;
            synchronized (PackSync.class) {
                entry = ENTRIES.get(address);
            }
            if (entry == null || entry.file == null || entry.sha1 == null) {
                return;
            }
            Path zip = Path.of("resourcepacks", entry.file);
            if (!Files.isRegularFile(zip) || !equalsHex(sha1HexOfFile(zip), entry.sha1)) {
                return;
            }
            applyPack(client, entry.file);
        } catch (Exception ignored) {
        }
    }

    /* ========================================================
     *  Join: handshake pack_manifest
     * ======================================================== */

    /** {@code pack_manifest} = {@code url|sha1} vindo do WolfPlugin. */
    public static void onPackManifest(MinecraftClient client, WolfResponder responder, String value) {
        try {
            if (client == null || value == null) {
                return;
            }
            String[] parts = value.split("\\|", 2);
            if (parts.length < 2) {
                return;
            }
            String zipUrl = parts[0].trim();
            String sha1 = parts[1].trim().toLowerCase(java.util.Locale.ROOT);
            if (zipUrl.isEmpty() || sha1.isEmpty()) {
                return;
            }
            ServerInfo server = client.getCurrentServerEntry();
            if (server == null || server.address == null || server.address.isBlank()) {
                return;
            }
            String address = server.address;
            synchronized (PackSync.class) {
                load();
                lastAddress = address;
                Entry entry = ENTRIES.get(address);
                if (entry == null) {
                    entry = new Entry();
                    entry.file = safeName(address);
                    ENTRIES.put(address, entry);
                }
                entry.zipUrl = zipUrl;
                entry.manifestUrl = deriveManifestUrl(zipUrl);
                if (!equalsHex(entry.sha1, sha1)) {
                    entry.sha1 = null;
                }
                save();
            }
            Path zip = ensureVerified(address, zipUrl, sha1);
            if (zip == null) {
                return;
            }
            Entry entry;
            synchronized (PackSync.class) {
                entry = ENTRIES.get(address);
                if (entry == null) {
                    return;
                }
                entry.sha1 = sha1;
                save();
            }
            applyPack(client, entry.file);
            if (responder != null) {
                responder.sendConfig(new WolfConfigPayload("pack_ready", sha1));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Retorna o zip verificado contra o SHA-1 vigente, ou {@code null}.
     * Quando desatualizado/ausente, agenda o download em background para o
     * proximo join e devolve {@code null} (fluxo vanilla dessa vez).
     */
    private static Path ensureVerified(String address, String zipUrl, String sha1) {
        Entry entry;
        synchronized (PackSync.class) {
            entry = ENTRIES.get(address);
        }
        if (entry == null || entry.file == null) {
            return null;
        }
        Path zip = Path.of("resourcepacks", entry.file);
        try {
            if (Files.isRegularFile(zip) && equalsHex(sha1HexOfFile(zip), sha1)) {
                return zip;
            }
        } catch (Exception ignored) {
        }
        Thread thread = new Thread(() -> {
            String fileName;
            synchronized (PackSync.class) {
                Entry current = ENTRIES.get(address);
                if (current == null || current.file == null) {
                    return;
                }
                fileName = current.file;
            }
            Path tmp = downloadToTemp(zipUrl, fileName);
            if (tmp == null) {
                return;
            }
            synchronized (PackSync.class) {
                try {
                    Entry current = ENTRIES.get(address);
                    if (current == null || !fileName.equals(current.file)) {
                        deleteQuietly(tmp);
                        return;
                    }
                    if (!equalsHex(sha1HexOfFile(tmp), sha1)) {
                        deleteQuietly(tmp);
                        return;
                    }
                    Path target = Path.of("resourcepacks", fileName);
                    Files.createDirectories(target.getParent());
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    current.sha1 = sha1;
                    save();
                } catch (Exception ignored) {
                    deleteQuietly(tmp);
                }
            }
        }, "WolfMOD-PackSync");
        thread.setDaemon(true);
        thread.start();
        return null;
    }

    /** Baixa o zip para um .part e devolve o caminho, ou {@code null}. Sem lock. */
    private static Path downloadToTemp(String zipUrl, String fileName) {
        if (zipUrl == null || zipUrl.isBlank() || fileName == null) {
            return null;
        }
        try {
            Path target = Path.of("resourcepacks", fileName);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(fileName + ".part");
            download(zipUrl, tmp);
            return tmp;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }

    /* ========================================================
     *  Aplicacao local (sem prompt, sem download)
     * ======================================================== */

    /**
     * Ativa o zip como pack global e recarrega recursos. Devolve {@code true}
     * se o pack ficou ativo (ja estava ou foi ativado).
     */
    private static boolean applyPack(MinecraftClient client, String fileName) {
        try {
            ResourcePackManager manager = client.getResourcePackManager();
            if (manager == null) {
                return false;
            }
            manager.scanPacks();
            String targetId = null;
            for (ResourcePackProfile profile : manager.getProfiles()) {
                String id = profile.getId();
                if (id != null && (id.equals("file/" + fileName) || id.endsWith("/" + fileName))) {
                    targetId = id;
                    break;
                }
            }
            if (targetId == null) {
                return false;
            }
            Collection<String> enabled = new ArrayList<>(manager.getEnabledIds());
            List<String> ours = new ArrayList<>();
            for (String id : enabled) {
                if (id != null && id.contains(FILE_PREFIX)) {
                    ours.add(id);
                }
            }
            boolean changed = false;
            for (String id : ours) {
                if (!id.equals(targetId)) {
                    manager.disable(id);
                    changed = true;
                }
            }
            if (!enabled.contains(targetId)) {
                manager.enable(targetId);
                changed = true;
            }
            if (changed) {
                client.reloadResources();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /* ========================================================
     *  Rede + hash + manifest
     * ======================================================== */

    private static final class Manifest {
        String sha1;
        String url;
    }

    private static Manifest fetchManifest(String manifestUrl) {
        try {
            String body = getText(manifestUrl);
            if (body == null) {
                return null;
            }
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            Manifest manifest = new Manifest();
            if (obj.has("sha1") && obj.get("sha1").isJsonPrimitive()) {
                manifest.sha1 = obj.get("sha1").getAsString().toLowerCase(java.util.Locale.ROOT);
            }
            if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
                manifest.url = obj.get("url").getAsString();
            }
            return manifest.sha1 == null ? null : manifest;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String deriveManifestUrl(String zipUrl) {
        try {
            return URI.create(zipUrl).resolve("/manifest.json").toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getText(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "WolfMOD");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            byte[] body = connection.getInputStream().readAllBytes();
            if (body.length > 65536) {
                return null;
            }
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void download(String url, Path target) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "WolfMOD");
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("HTTP " + connection.getResponseCode());
            }
            try (var in = connection.getInputStream();
                 var out = Files.newOutputStream(target,
                         java.nio.file.StandardOpenOption.CREATE,
                         java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new java.io.IOException("pack grande demais");
                    }
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String sha1HexOfFile(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean equalsHex(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
