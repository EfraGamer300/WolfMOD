package dev.EfraGroup.wolfmod.platform;

import dev.EfraGroup.wolfmod.network.GhostUploadPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;

/**
 * Ponte de rede client-side. O :common nao conhece nenhuma API de loader, entao cada
 * modulo (fabric/neoforge/quilt/forge) registra a sua implementacao no boot e o codigo
 * compartilhado so enxerga esta interface.
 *
 * Equivale ao que o WolfMOD usava direto do Fabric:
 * ClientPlayNetworking.canSend(id) -> canSendConfig()/canSendRadio()
 * ClientPlayNetworking.send(p)      -> sendConfig(p)/sendRadio(p)
 */
public final class WolfNetwork {
    private static WolfNetworkBridge bridge = NoopBridge.INSTANCE;

    private WolfNetwork() {
    }

    public static void install(WolfNetworkBridge impl) {
        bridge = impl == null ? NoopBridge.INSTANCE : impl;
    }

    public static WolfNetworkBridge bridge() {
        return bridge;
    }

    public static boolean canSendConfig() {
        return bridge.canSendConfig();
    }

    public static void sendConfig(WolfConfigPayload payload) {
        bridge.sendConfig(payload);
    }

    public static boolean canSendRadio() {
        return bridge.canSendRadio();
    }

    public static void sendRadio(RadioPayload payload) {
        bridge.sendRadio(payload);
    }

    public static boolean canSendGhostUpload() {
        return bridge.canSendGhostUpload();
    }

    public static void sendGhostUpload(GhostUploadPayload payload) {
        bridge.sendGhostUpload(payload);
    }

    private static final class NoopBridge implements WolfNetworkBridge {
        private static final NoopBridge INSTANCE = new NoopBridge();

        @Override
        public boolean canSendConfig() {
            return false;
        }

        @Override
        public void sendConfig(WolfConfigPayload payload) {
        }

        @Override
        public boolean canSendRadio() {
            return false;
        }

        @Override
        public void sendRadio(RadioPayload payload) {
        }

        @Override
        public boolean canSendGhostUpload() {
            return false;
        }

        @Override
        public void sendGhostUpload(GhostUploadPayload payload) {
        }
    }
}
