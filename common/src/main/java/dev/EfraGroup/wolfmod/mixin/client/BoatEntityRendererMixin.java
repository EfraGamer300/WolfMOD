package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forca o barco a renderizar na camada translucida para que o alpha do
 * ghost (aplicado no redirect do modelo) seja respeitado pelo blend.
 *
 * <p>Usa {@code @Inject} no retorno em vez de um redirect da chamada interna:
 * o alvo do redirect (metodo herdado {@code Model.getLayer}) era remapeado
 * erroneamente no build de producao, quebrando a injecao e crashando o jogo.
 * Espelha o BoatLabs: vale para todos os barcos, mas com textura opaca e alpha 1
 * o resultado visual e identico ao da camada padrao.
 */
@Mixin(BoatEntityRenderer.class)
public abstract class BoatEntityRendererMixin {
    @Shadow
    @Final
    private Identifier texture;

    @Inject(
            method = "getRenderLayer()Lnet/minecraft/client/render/RenderLayer;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void wolfmod$ghostTranslucentLayer(CallbackInfoReturnable<RenderLayer> cir) {
        cir.setReturnValue(RenderLayer.getEntityTranslucent(this.texture));
    }
}
