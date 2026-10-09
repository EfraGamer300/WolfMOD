package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.entity.state.BoatEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BoatEntityRenderState.class)
public class BoatEntityRenderStateMixin implements GhostRenderStateAccessor {
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
