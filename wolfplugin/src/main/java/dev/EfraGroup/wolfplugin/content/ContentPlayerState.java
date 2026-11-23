package dev.EfraGroup.wolfplugin.content;

// Estado por jogador do inventario WolfContent (Fase 1, so metrica, sem kick).
public final class ContentPlayerState {
    private volatile String installedVersion = "";
    private volatile String loader = "";
    private volatile String gameVersion = "";
    private volatile String modVersion = "";
    private volatile long lastSeen = 0L;
    private volatile boolean hasMod = false;

    public String getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(String v) {
        this.installedVersion = v == null ? "" : v;
    }

    public String getLoader() {
        return loader;
    }

    public void setLoader(String v) {
        this.loader = v == null ? "" : v;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public void setGameVersion(String v) {
        this.gameVersion = v == null ? "" : v;
    }

    public String getModVersion() {
        return modVersion;
    }

    public void setModVersion(String v) {
        this.modVersion = v == null ? "" : v;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long v) {
        this.lastSeen = v;
    }

    public boolean hasMod() {
        return hasMod;
    }

    public void setHasMod(boolean v) {
        this.hasMod = v;
    }
}
