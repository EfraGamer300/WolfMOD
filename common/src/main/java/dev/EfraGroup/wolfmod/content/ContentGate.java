package dev.EfraGroup.wolfmod.content;

import dev.EfraGroup.wolfmod.client.ServerInfoManager;

/**
 * WolfContent Fase 1 — gate de ativacao.
 *
 * <p>O conteudo WolfContent so "acorda" quando as duas condicoes valem:
 * (1) o endereco do servidor e da WolfNetwork e (2) um hello valido
 * ({@code wc_v1_hello}) foi recebido nesta sessao. Fora disso o mod fica
 * dormente: nada de abas, receitas, traducoes ou ticks de conteudo.</p>
 */
public final class ContentGate {
    private static volatile boolean helloReceived;
    private static volatile boolean devOverride;

    private ContentGate() {
    }

    /** Marca que um hello valido chegou (chamado pelo responder). */
    public static void markHelloReceived() {
        helloReceived = true;
    }

    /** Flag explicita p/ teste local (nunca ligada por padrao). */
    public static void setDevOverride(boolean value) {
        devOverride = value;
    }

    public static boolean isActive() {
        if (devOverride) {
            return true;
        }
        return helloReceived && ServerInfoManager.isOnWolfNetwork();
    }

    /** Chamado no disconnect: volta a dormir. */
    public static void reset() {
        helloReceived = false;
    }
}
