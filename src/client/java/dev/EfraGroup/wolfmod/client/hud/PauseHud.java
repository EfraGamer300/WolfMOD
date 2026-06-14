package dev.EfraGroup.wolfmod.client.hud;

import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public class PauseHud {

    private static final Identifier WOLF_LOGO = Identifier.of("wolfmod", "textures/gui/wolf_icon.png");

    private static final int BG_COLOR = 0xCC1a1a2e;
    private static final int BORDER_COLOR = 0xFF800080;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int TEXT_PURPLE = 0xFFc084fc;

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Só renderiza se estiver na tela de pause (ESC)
        if (!(client.currentScreen instanceof GameMenuScreen)) {
            return;
        }

        // Só renderiza se estiver na WolfNetwork
        ServerInfoManager.checkCurrentServer();
        if (!ServerInfoManager.isOnWolfNetwork()) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();

        String networkName = ServerInfoManager.getDisplayServerName();
        String serverLabel = "Servidor:";
        String serverValue = ServerInfoManager.getCurrentSubServer();

        int padding = 8;
        int iconSize = 16;
        int lineHeight = renderer.fontHeight + 2;

        int textWidthNetwork = renderer.getWidth(networkName);
        int textWidthLabel = renderer.getWidth(serverLabel);
        int textWidthValue = renderer.getWidth(serverValue);
        int maxTextWidth = Math.max(textWidthNetwork, Math.max(textWidthLabel, textWidthValue));

        int boxWidth = maxTextWidth + iconSize + padding * 3;
        int boxHeight = padding * 2 + lineHeight * 3 + 4;

        int x = screenWidth - boxWidth - 20;
        int y = 15      ;

        // Fundo com borda
        context.fill(x, y, x + boxWidth, y + boxHeight, BG_COLOR);
        context.fill(x, y, x + boxWidth, y + 1, BORDER_COLOR);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, BORDER_COLOR);
        context.fill(x, y, x + 1, y + boxHeight, BORDER_COLOR);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, BORDER_COLOR);

        // Ícone
        context.drawTexture(RenderLayer::getGuiTextured, WOLF_LOGO,
                x + padding, y + padding,
                0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize, 0xFFFFFFFF);

        // Nome da network
        int textX = x + padding + iconSize + padding;
        int textY = y + padding;
        context.drawTextWithShadow(renderer, networkName, textX, textY, TEXT_PURPLE);

        // Label "Servidor:"
        textY += lineHeight + 2;
        context.drawTextWithShadow(renderer, serverLabel, textX, textY, TEXT_GRAY);

        // Valor do servidor
        textY += lineHeight;
        context.drawTextWithShadow(renderer, serverValue, textX, textY, TEXT_WHITE);
    }
}

