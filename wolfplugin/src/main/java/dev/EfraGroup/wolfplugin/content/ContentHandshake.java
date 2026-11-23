package dev.EfraGroup.wolfplugin.content;

import dev.EfraGroup.wolfplugin.utils.VarIntUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

// Handshake WolfContent Fase 1 (canal wolfnetwork:settings, codec VarInt).
// Hello em task separada (nao conflita com handshake "1" existente).
// Recebe wc_v1_status, guarda estado. Check 10s: sem status = sem-mod.
public final class ContentHandshake {
    static final String CHANNEL = "wolfnetwork:settings";
    private static final long HELLO_DELAY_TICKS = 60L;
    private static final long CHECK_DELAY_TICKS = 200L;

    private final JavaPlugin plugin;
    private final String requiredVersion;
    private final String minVersion;
    private final String manifestUrl;
    private final String serverId;
    private final ContentStats stats = new ContentStats();
    private final Map<UUID, ContentPlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> helloTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> checkTasks = new ConcurrentHashMap<>();
    private BukkitTask statsTask;

    public ContentHandshake(JavaPlugin plugin, String requiredVersion, String minVersion,
            String manifestUrl, String serverId) {
        this.plugin = plugin;
        this.requiredVersion = requiredVersion == null ? "0" : requiredVersion;
        this.minVersion = minVersion == null ? "0" : minVersion;
        this.manifestUrl = manifestUrl == null ? "" : manifestUrl;
        this.serverId = serverId == null ? "wolfnetwork" : serverId;
    }

    public void start() {
        if (statsTask == null) {
            statsTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin, () -> stats.logSummary(plugin.getLogger()), 6000L, 6000L);
        }
    }

    public void stop() {
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
        for (BukkitTask t : helloTasks.values()) {
            t.cancel();
        }
        helloTasks.clear();
        for (BukkitTask t : checkTasks.values()) {
            t.cancel();
        }
        checkTasks.clear();
        states.clear();
    }

    public ContentStats getStats() {
        return stats;
    }

    public void onPlayerJoin(Player player) {
        stats.recordJoin();
        states.putIfAbsent(player.getUniqueId(), new ContentPlayerState());
        cancel(helloTasks, player.getUniqueId());
        cancel(checkTasks, player.getUniqueId());
        BukkitTask hello = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            helloTasks.remove(player.getUniqueId());
            if (player.isOnline()) {
                sendHello(player);
            }
        }, HELLO_DELAY_TICKS);
        helloTasks.put(player.getUniqueId(), hello);
        BukkitTask check = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkTasks.remove(player.getUniqueId());
            ContentPlayerState st = states.get(player.getUniqueId());
            if (st != null && !st.hasMod() && player.isOnline()) {
                plugin.getLogger().info("[WolfContent] " + player.getName() + " sem-mod (sem status 10s).");
            }
        }, CHECK_DELAY_TICKS);
        checkTasks.put(player.getUniqueId(), check);
    }

    public void handleIncoming(Player player, String key, String value) {
        if (!ContentProtocol.STATUS.equals(key)) {
            return;
        }
        String protocol = ContentProtocol.extractJsonString(value, "protocol");
        if (!"1".equals(protocol)) {
            return;
        }
        ContentPlayerState st = states.computeIfAbsent(player.getUniqueId(), k -> new ContentPlayerState());
        String installed = ContentProtocol.extractJsonString(value, "installedVersion");
        String loader = ContentProtocol.extractJsonString(value, "loader");
        String game = ContentProtocol.extractJsonString(value, "gameVersion");
        String mod = ContentProtocol.extractJsonString(value, "modVersion");
        st.setInstalledVersion(installed == null ? "" : installed);
        st.setLoader(loader == null ? "" : loader);
        st.setGameVersion(game == null ? "" : game);
        st.setModVersion(mod == null ? "" : mod);
        st.setLastSeen(System.currentTimeMillis());
        st.setHasMod(true);
        boolean fresh = compare(st.getInstalledVersion(), requiredVersion) >= 0;
        stats.recordStatus(player.getUniqueId(), st.getInstalledVersion(), st.getLoader(), fresh);
        plugin.getLogger().info("[WolfContent] " + player.getName()
            + " status v" + st.getInstalledVersion() + " loader=" + st.getLoader());
    }

    private void sendHello(Player player) {
        String json = ContentProtocol.buildHello(requiredVersion, minVersion, manifestUrl, serverId);
        player.sendPluginMessage(plugin, CHANNEL, VarIntUtils.encodeString(ContentProtocol.HELLO, json));
    }

    /** Comparacao numerica ("9" < "12"); nao-numerico cai p/ lexicografica. */
    static int compare(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a.trim()), Integer.parseInt(b.trim()));
        } catch (Exception e) {
            return String.valueOf(a).compareTo(String.valueOf(b));
        }
    }

    private static void cancel(Map<UUID, BukkitTask> map, UUID id) {
        BukkitTask t = map.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    public void onPlayerQuit(Player player) {
        cancel(helloTasks, player.getUniqueId());
        cancel(checkTasks, player.getUniqueId());
        states.remove(player.getUniqueId());
        stats.recordQuit(player.getUniqueId());
    }
}