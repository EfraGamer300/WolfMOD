package dev.EfraGroup.wolfmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public class FastestLap implements HudRenderCallback {

    private static final Identifier TIMER_ICON_TEXTURE = Identifier.of("wolfmod", "textures/gui/timer_icon.png");

    private static final int PURPLE_BG = 0xFF800080;
    private static final int BLACK_BG = 0xFF000000;
    private static final int PURPLE_TEXT_RGB = 0x00A020F0;
    private static final int WHITE_TEXT = 0xFFFFFFFF; // Branco para o tempo

    private static int displayTicksRemaining = 0;
    private static final int TOTAL_DURATION = 110;

    private static String currentPlayer = "EfraMLG";
    private static String lapTime = "1:35.323"; // Tempo da volta

    public static void show() {
        displayTicksRemaining = TOTAL_DURATION;
    }

    public static void show(String playerName, String time) {
        currentPlayer = playerName;
        lapTime = time;
        displayTicksRemaining = TOTAL_DURATION;
    }

    public static void tick() {
        if (displayTicksRemaining > 0) {
            displayTicksRemaining--;
        }
    }

    private static int getAlphaColor(int rgbColor, float alphaProgress) {
        int alpha = (int) (alphaProgress * 255.0f);
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgbColor & 0x00FFFFFF);
    }

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (displayTicksRemaining <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        int ticksElapsed = TOTAL_DURATION - displayTicksRemaining;
        int targetY = 40;
        int offScreenY = -40;
        int currentY = targetY;

        String textToRender = "FASTEST LAP";
        float textAlpha = 1.0f;
        boolean showDetails = false; // Controla se mostra Jogador + Tempo

        // Animações de Movimento
        if (ticksElapsed <= 10) {
            float progress = ticksElapsed / 10.0f;
            currentY = (int) (offScreenY + ((targetY - offScreenY) * progress));
        } else if (ticksElapsed >= 100) {
            float progress = (ticksElapsed - 100) / 10.0f;
            currentY = (int) (targetY - ((targetY - offScreenY) * progress));
        }

        // Animação de Transição de Texto
        if (ticksElapsed >= 70 && ticksElapsed <= 75) {
            textAlpha = 1.0f - ((ticksElapsed - 70) / 5.0f);
        } else if (ticksElapsed > 75 && ticksElapsed <= 80) {
            showDetails = true;
            textAlpha = (ticksElapsed - 75) / 5.0f;
        } else if (ticksElapsed > 80) {
            showDetails = true;
            textAlpha = 1.0f;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int squareSize = 26;
        int rectWidth = 100; // Aumentei um pouco a largura para caber melhor o tempo
        int totalWidth = squareSize + rectWidth;
        int x = (screenWidth / 2) - (totalWidth / 2);

        // Renderização dos Fundos
        drawContext.fill(x + squareSize, currentY, x + totalWidth, currentY + squareSize, BLACK_BG);
        drawContext.fill(x, currentY, x + squareSize, currentY + squareSize, PURPLE_BG);

        // Renderização do Ícone
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        drawContext.drawTexture(RenderLayer::getGuiTextured, TIMER_ICON_TEXTURE,
                x + (squareSize - 18) / 2, currentY + (squareSize - 18) / 2,
                0.0F, 0.0F, 18, 18, 18, 18, 0xFFFFFFFF);

        // Renderização do Texto
        if (textAlpha > 0.05f) {
            if (!showDetails) {
                // Renderiza apenas "FASTEST LAP" centralizado
                int textX = x + squareSize + (rectWidth - client.textRenderer.getWidth(textToRender)) / 2;
                int textY = currentY + (squareSize - client.textRenderer.fontHeight) / 2 + 1;
                drawContext.drawTextWithShadow(client.textRenderer, textToRender, textX, textY, getAlphaColor(PURPLE_TEXT_RGB, textAlpha));
            } else {
                // Renderiza Nome (em cima) e Tempo (embaixo)
                int nameX = x + squareSize + (rectWidth - client.textRenderer.getWidth(currentPlayer)) / 2;
                int timeX = x + squareSize + (rectWidth - client.textRenderer.getWidth(lapTime)) / 2;

                // Ajuste de Y para empilhar os textos
                int nameY = currentY + 4;
                int timeY = currentY + 14;

                drawContext.drawTextWithShadow(client.textRenderer, currentPlayer, nameX, nameY, getAlphaColor(PURPLE_TEXT_RGB, textAlpha));
                drawContext.drawTextWithShadow(client.textRenderer, lapTime, timeX, timeY, getAlphaColor(WHITE_TEXT, textAlpha));
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}