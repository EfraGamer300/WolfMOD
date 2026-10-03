package dev.EfraGroup.wolfmod.platform;

import dev.EfraGroup.wolfmod.network.WolfConfigPayload;

/**
 * Substitui o PacketSender do Fabric API. O timing client so envia WolfConfigPayload,
 * entao a assinatura e estreita de proposito - assim o :common nao depende do
 * CustomPayload genérico que cada loader nomeia de um jeito.
 */
@FunctionalInterface
public interface WolfResponder {
    void sendConfig(WolfConfigPayload payload);

    static WolfResponder of(WolfNetworkBridge bridge) {
        return bridge::sendConfig;
    }
}
