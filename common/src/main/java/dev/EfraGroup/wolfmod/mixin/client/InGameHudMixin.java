package dev.EfraGroup.wolfmod.mixin.client;

import dev.EfraGroup.wolfmod.client.GhostConfig;
import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla actionbar (overlay message) while a lap is being timed, so the server's
 * "checkpoint 2/5" style messages do not fight the mod's timer HUD.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void wolfmod$hideOverlayDuringTiming(Text message, boolean tinted, CallbackInfo ci) {
        if (Timer.isRunning() && GhostConfig.get().useTimeTrialTimer) {
            ci.cancel();
        }
    }
}
