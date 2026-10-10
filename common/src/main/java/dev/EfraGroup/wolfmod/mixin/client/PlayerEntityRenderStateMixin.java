package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements GhostRenderStateAccessor {
    @Unique
    private boolean wolfmod$ghost;

    @Unique
    @Override
    public void wolfmod$setGhost(boolean ghost) {
        this.wolfmod$ghost = ghost;
    }

    @Unique
    @Override
    public boolean wolfmod$isGhost() {
        return this.wolfmod$ghost;
    }
}
