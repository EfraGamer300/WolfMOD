package dev.EfraGroup.wolfmod.platform;

/**
 * Lista de mods instalada. O :common precisa disso para responder ao
 * "wolfac_checkmods" do servidor, mas cada loader expoe a sua propria API
 * (FabricLoader / ModList do Forge / QuiltLoader), entao o modulo registra
 * a implementacao no boot.
 */
public final class WolfModList {
    /** Devolve "id:version,id:version,..." */
    public interface Provider {
        String describe();
    }

    private static final Provider EMPTY = () -> "";
    private static Provider provider = EMPTY;

    private WolfModList() {
    }

    public static void install(Provider impl) {
        provider = impl == null ? EMPTY : impl;
    }

    public static String describe() {
        try {
            return provider.describe();
        } catch (Throwable e) {
            return "error:" + e.getMessage();
        }
    }

    public static String describeTruncated(int limit) {
        String mods = describe();
        if (mods.length() > limit) mods = mods.substring(0, limit);
        return mods;
    }
}
