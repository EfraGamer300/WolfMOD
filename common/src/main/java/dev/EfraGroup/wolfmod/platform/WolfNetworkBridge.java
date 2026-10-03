package dev.EfraGroup.wolfmod.platform;

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
}
