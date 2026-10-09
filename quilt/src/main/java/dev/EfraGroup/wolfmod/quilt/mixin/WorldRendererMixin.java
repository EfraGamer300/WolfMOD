package dev.EfraGroup.wolfmod.quilt.mixin;

import dev.EfraGroup.wolfmod.client.WolfClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O QFAPI nao tem equivalente do WorldRenderEvents.AFTER_ENTITIES. O ghost e
 * desenhado dentro do render do mundo, no TAIL do WorldRenderer.render.
 *
 * ATENCAO: este e o ponto mais fragil do porte - a assinatura de
 * WorldRenderer.render mudou varias vezes entre 1.21 e 1.21.4. Se o mixin
 * falhar ao carregar, o log lista as assinaturas candidatas e basta ajustar o
 * metodo e os argumentos daqui.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void wolfmod$renderGhost(
            MatrixStack matrices,
            float tickDelta,
            long finishTimeNanos,
            boolean renderBlockOutline,
            net.minecraft.client.render.Camera camera,
            net.minecraft.client.render.Frustum frustum,
            VertexConsumerProvider consumers,
            CallbackInfo ci
    ) {
        WolfClient.renderGhost(matrices, consumers, camera, tickDelta);
    }
}
