package dev.EfraGroup.wolfplugin.content;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

// Contadores thread-safe do inventario WolfContent. Metrica atual (online)
// + total acumulado de joins. Sem kick, sem permissao nova.
public final class ContentStats {
    private final AtomicInteger totalJoins = new AtomicInteger();
    private final AtomicInteger withMod = new AtomicInteger();
    private final AtomicInteger upToDate = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> byVersion = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> byLoader = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> trackedVersion = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> trackedLoader = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> trackedFresh = new ConcurrentHashMap<>();

    public void recordJoin() {
        totalJoins.incrementAndGet();
    }

    // Chamado quando um status chega (primeira vez ou reenvio).
    public void recordStatus(UUID id, String version, String loader, boolean fresh) {
        String v = norm(version);
        String l = norm(loader);
        String oldV = trackedVersion.put(id, v);
        String oldL = trackedLoader.put(id, l);
        Boolean oldFresh = trackedFresh.put(id, fresh);
        if (oldV == null) {
            withMod.incrementAndGet();
            incr(byVersion, v);
            incr(byLoader, l);
            if (fresh) {
                upToDate.incrementAndGet();
            }
            return;
        }
        if (!oldV.equals(v)) {
            decr(byVersion, oldV);
            incr(byVersion, v);
        }
        if (!oldL.equals(l)) {
            decr(byLoader, oldL);
            incr(byLoader, l);
        }
        boolean was = oldFresh != null && oldFresh;
        if (was != fresh) {
            if (fresh) {
                upToDate.incrementAndGet();
            } else {
                upToDate.decrementAndGet();
            }
        }
    }

    public void recordQuit(UUID id) {
        String v = trackedVersion.remove(id);
        String l = trackedLoader.remove(id);
        Boolean f = trackedFresh.remove(id);
        if (v == null) {
            return;
        }
        withMod.decrementAndGet();
        decr(byVersion, v);
        decr(byLoader, l);
        if (f != null && f) {
            upToDate.decrementAndGet();
        }
    }

    public int getTotal() {
        return totalJoins.get();
    }

    public int getWithMod() {
        return withMod.get();
    }

    public int getUpToDate() {
        return upToDate.get();
    }

    public String summary() {
        return "[WolfContent] joins=" + getTotal()
                + " withMod=" + getWithMod()
                + " upToDate=" + getUpToDate()
                + " byVersion=" + mapStr(byVersion)
                + " byLoader=" + mapStr(byLoader);
    }

    // Log periodico barato (chamado pelo handshake a cada ~5min).
    public void logSummary(Logger log) {
        log.info(summary());
    }

    private static String norm(String s) {
        if (s == null || s.isEmpty()) {
            return "unknown";
        }
        return s;
    }

    private static void incr(ConcurrentHashMap<String, AtomicInteger> m, String k) {
        m.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet();
    }

    private static void decr(ConcurrentHashMap<String, AtomicInteger> m, String k) {
        AtomicInteger c = m.get(k);
        if (c == null) {
            return;
        }
        if (c.decrementAndGet() <= 0) {
            m.remove(k, c);
        }
    }

    private static String mapStr(Map<String, AtomicInteger> m) {
        if (m.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue().get());
        }
        return sb.append('}').toString();
    }
}
