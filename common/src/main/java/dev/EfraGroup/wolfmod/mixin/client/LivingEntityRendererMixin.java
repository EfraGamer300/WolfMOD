package dev.EfraGroup.wolfmod.mixin.client;

import dev.EfraGroup.wolfmod.client.ghost.GhostPlayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render do piloto falso: marca o render state em {@code updateRenderState},
 * re-renderiza o modelo com alpha + tint amarelo e suprime a nametag.
 * Espelha o BoatLabs. O redirect so transforma o metodo alvo, sem recursao.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    /** Amarelo com ~50% de alpha (ARGB), multiplicado na skin do piloto. */
    private static final int GHOST_PLAYER_COLOR = 0x80FFFF00;

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            )
    )
    private void wolfmod$renderGhostPlayerTransparent(EntityModel<S> model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, S state) {
        if (state instanceof PlayerEntityRenderState playerState && GhostRenderStateAccessor.get(playerState).wolfmod$isGhost()) {
            model.render(matrices, vertices, light, overlay, GHOST_PLAYER_COLOR);
        } else {
            model.render(matrices, vertices, light, overlay, color);
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void wolfmod$markGhostState(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (state instanceof PlayerEntityRenderState playerState) {
            GhostRenderStateAccessor.get(playerState).wolfmod$setGhost(entity instanceof GhostPlayer);
        }
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void wolfmod$disableGhostPlayerNameTag(T entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof GhostPlayer) {
            cir.setReturnValue(false);
        }
    }
}
