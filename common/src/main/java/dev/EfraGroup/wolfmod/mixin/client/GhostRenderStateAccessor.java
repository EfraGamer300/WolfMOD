package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.entity.state.BoatEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * Flag de ghost no render state do barco, preenchida em
 * {@code updateRenderState} e lida no redirect do render do modelo.
 * Espelha o {@code TransparentFieldAccessor} do BoatLabs.
 */
public interface GhostRenderStateAccessor {
    void wolfmod$setGhost(boolean ghost);

    boolean wolfmod$isGhost();

    static GhostRenderStateAccessor get(BoatEntityRenderState state) {
        return (GhostRenderStateAccessor) state;
    }

    static GhostRenderStateAccessor get(PlayerEntityRenderState state) {
        return (GhostRenderStateAccessor) state;
    }
}
