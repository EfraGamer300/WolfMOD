package dev.EfraGroup.wolfmod.content;

/**
 * WolfContent Fase 1 — versao do motor (codigo) separada da versao do pacote (dados).
 *
 * <p>{@code CURRENT} e o modVersion enviado no {@code wc_v1_status}. O pacote de dados
 * declara {@code requiresEngine} e o prompt da Fase 4 usa essa comparacao para decidir
 * entre "baixar dados" (comum) e "atualizar motor" (raro).</p>
 */
public final class ContentModVersion {
    /** Versao do motor. Bump manual quando entrar verbo novo ou mudar o protocolo. */
    public static final String CURRENT = "1.0.0";

    private ContentModVersion() {
    }
}
