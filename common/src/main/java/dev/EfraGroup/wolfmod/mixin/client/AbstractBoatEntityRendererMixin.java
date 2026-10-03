package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.entity.AbstractBoatEntityRenderer;
import net.minecraft.client.render.entity.state.BoatEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoatEntityRenderer.class)
public class AbstractBoatEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/vehicle/AbstractBoatEntity;Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;F)V", at = @At("TAIL"))
    private void wolfmod$updateVisualState(AbstractBoatEntity boat, BoatEntityRenderState state, float tickDelta, CallbackInfo ci) {
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/BoatEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void wolfmod$applyExtraBoatRotation(BoatEntityRenderState state, MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
    }
}
