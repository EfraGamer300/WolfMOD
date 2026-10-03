package dev.EfraGroup.wolfmod.quilt.mixin;

import dev.EfraGroup.wolfmod.client.WolfClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O QFAPI nao tem equivalente do HudRenderCallback do Fabric API. Este mixin
 * entra no DrawContext.draw() - que e o ponto onde o InGameHud entrega tudo
 * que desenhou - e chama o mesmo WolfClient que o Fabric usa.
 *
 * HEAD para desenhar antes do flush do vanilla.
 */
@Mixin(DrawContext.class)
public class DrawContextMixin {

    @Inject(method = "draw()V", at = @At("HEAD"))
    private void wolfmod$drawHud(CallbackInfo ci) {
        DrawContext context = (DrawContext) (Object) this;
        WolfClient.renderHud(context, 0.0F);
        WolfClient.renderFastestLap(context);
    }
}
