package dev.EfraGroup.wolfmod.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Fallback shown when Mod Menu is installed without Yet Another Config Lib. */
public final class MissingYaclScreen extends Screen {
    public MissingYaclScreen(Screen parent) {
        super(Text.literal("WolfMOD"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Yet Another Config Lib (YACL) não instalado"),
                this.width / 2,
                this.height / 2 - 10,
                0xFFFFFF);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Instale o YACL para abrir a configuração do WolfMOD."),
                this.width / 2,
                this.height / 2 + 10,
                0xAAAAAA);
    }
}
