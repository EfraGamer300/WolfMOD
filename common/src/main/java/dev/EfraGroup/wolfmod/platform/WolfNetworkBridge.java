package dev.EfraGroup.wolfmod.platform;

import dev.EfraGroup.wolfmod.network.GhostUploadPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;

/**
 * Implementada pelo modulo de cada loader. Ver {@link WolfNetwork}.
 */
public interface WolfNetworkBridge {

    boolean canSendConfig();

    void sendConfig(WolfConfigPayload payload);

    boolean canSendRadio();

    void sendRadio(RadioPayload payload);

    /**
     * Envia um trecho da volta gravada pelo cliente. Separate de
     * {@link #sendConfig(WolfConfigPayload)} porque o canal de configuracao carrega JSON
     * pequeno, enquanto a volta e binaria e chega em varios pacotes.
     */
    boolean canSendGhostUpload();

    void sendGhostUpload(GhostUploadPayload payload);
}
