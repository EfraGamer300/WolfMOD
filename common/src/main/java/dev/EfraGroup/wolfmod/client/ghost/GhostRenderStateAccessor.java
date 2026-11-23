package dev.EfraGroup.wolfmod.client.ghost;

import net.minecraft.client.render.entity.state.BoatEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * Flag de ghost no render state do barco, preenchida em
 * {@code updateRenderState} e lida no redirect do render do modelo.
 * Espelha o {@code TransparentFieldAccessor} do BoatLabs.
 *
 * NOTA: esta interface NAO pode ficar no pacote de mixin
 * (dev.EfraGroup.wolfmod.mixin.client). As mixins a implementam, o que
 * faz a classe vanilla transformada ficar "implements" dela; quando o
 * classloader carrega a classe vanilla, ele tenta carregar esta interface
 * e o Mixin bloqueia classes do pacote de mixin (IllegalClassLoadError).
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
