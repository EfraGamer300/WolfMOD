package dev.EfraGroup.wolfmod.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forca o barco a renderizar na camada translucida para que o alpha do
 * ghost (aplicado no redirect do modelo) seja respeitado pelo blend.
 * Espelha o BoatLabs: vale para todos os barcos, mas com textura opaca
 * e alpha 1 o resultado visual e identico ao da camada padrao.
 */
@Mixin(BoatEntityRenderer.class)
public class BoatEntityRendererMixin {
    @Redirect(
            method = "getRenderLayer()Lnet/minecraft/client/render/RenderLayer;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;getLayer(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
            )
    )
    private RenderLayer wolfmod$ghostTranslucentLayer(EntityModel<?> model, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
