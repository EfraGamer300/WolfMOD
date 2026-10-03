package dev.EfraGroup.wolfmod;

/**
 * Ponto de entrada comum. Nao implementa ModInitializer de proposito: cada modulo de
 * loader tem o seu proprio entrypoint (que roda no lado comum) e chama
 * {@link #onInitialize()}.
 */
public final class Wolfmod {
    public static final String MOD_ID = "wolfnod";

    private Wolfmod() {
    }

    public static void onInitialize() {
    }
}
