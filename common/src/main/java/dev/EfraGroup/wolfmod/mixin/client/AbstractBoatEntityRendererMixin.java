package dev.EfraGroup.wolfmod.mixin.client;

import dev.EfraGroup.wolfmod.client.ghost.GhostBoat;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.AbstractBoatEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.BoatEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render do barco ghost: marca o render state em {@code updateRenderState}
 * e re-renderiza o modelo com alpha + tint amarelo em vez do render opaco.
 * Espelha o BoatLabs (redirect + flag no render state). Como o redirect so
 * transforma o metodo alvo, a chamada ao render original dentro do handler
 * nao e redirecionada de novo (sem recursao).
 */
@Mixin(AbstractBoatEntityRenderer.class)
public class AbstractBoatEntityRendererMixin {
    /** Amarelo com ~50% de alpha (ARGB), multiplicado na textura do barco. */
    private static final int GHOST_BOAT_COLOR = 0x80FFFF00;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/vehicle/AbstractBoatEntity;Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;F)V", at = @At("TAIL"))
    private void wolfmod$markGhostState(AbstractBoatEntity boat, BoatEntityRenderState state, float tickDelta, CallbackInfo ci) {
        GhostRenderStateAccessor.get(state).wolfmod$setGhost(boat instanceof GhostBoat);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
            )
    )
    private void wolfmod$renderGhostBoatTransparent(EntityModel<BoatEntityRenderState> model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, BoatEntityRenderState state) {
        if (GhostRenderStateAccessor.get(state).wolfmod$isGhost()) {
            model.render(matrices, vertices, light, overlay, GHOST_BOAT_COLOR);
        } else {
            model.render(matrices, vertices, light, overlay);
        }
    }
}
