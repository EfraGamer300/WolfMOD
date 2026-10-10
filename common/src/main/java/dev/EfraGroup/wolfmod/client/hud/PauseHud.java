package dev.EfraGroup.wolfmod.client.hud;

import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Painel da WolfNetwork na tela de pausa (ESC). Mostra a rede, o sub-servidor
 * e o logo. A paleta e a mesma do {@link Hud} para os dois painis combinarem.
 */
public class PauseHud {

    private static final Identifier WOLF_LOGO = Identifier.of("wolfmod", "textures/gui/wolf_icon.png");

    private static final int SHADOW = 0x60000000;
    private static final int BG = 0xF0121220;
    private static final int BORDER = 0xFF2E2847;
    private static final int TOP_SHINE = 0x24FFFFFF;
    private static final int ACCENT = 0xFFA020F0;
    private static final int ACCENT_DARK = 0xFF5B0BA6;
    private static final int TEXT_TITLE = 0xFFC084FC;
    private static final int TEXT_LABEL = 0xFF9A93B8;
    private static final int TEXT_VALUE = 0xFFFFFFFF;
    private static final int DOT_ON = 0xFF34D399;
    private static final int DOT_OFF = 0xFF5A1A1A;

    private static final int ACCENT_W = 3;
    private static final int PAD = 9;
    private static final int ROW_GAP = 3;
    private static final int DOT = 5;
    private static final int LOGO = 30;
    private static final int MARGIN = 20;

    private static final String LABEL = "Servidor";

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // So na tela de pausa (ESC).
        if (!(client.currentScreen instanceof GameMenuScreen)) {
            return;
        }

        ServerInfoManager.checkCurrentServer();
        if (!ServerInfoManager.isOnWolfNetwork()) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int fh = renderer.fontHeight;

        String network = ServerInfoManager.getDisplayServerName();
        String server = ServerInfoManager.getCurrentSubServer();

        // Mede as duas linhas: o titulo acima, o par label/valor abaixo.
        int labelW = renderer.getWidth(LABEL);
        int valueW = renderer.getWidth(server);
        int subRowW = DOT + 4 + Math.max(labelW, valueW);
        int networkW = renderer.getWidth(network);
        int textW = Math.max(subRowW, networkW);

        // O texto comeca depois do logo (textX = cx + LOGO + PAD), entao o logo
        // precisa entrar na largura alem do proprio texto. Sem isso o painel fica
        // estreito demais e o texto vaza para fora da borda.
        int contentW = LOGO + PAD + Math.max(textW, LOGO);
        int w = 1 + ACCENT_W + 1 + contentW + PAD + 1;
        int h = 1 + PAD + fh + ROW_GAP + fh + PAD + 1;

        int screenW = client.getWindow().getScaledWidth();
        int x = screenW - w - MARGIN;
        int y = MARGIN - 5;

        // Sombra, fundo e borda.
        context.fill(x - 2, y - 2, x + w + 2, y + h + 2, SHADOW);
        context.fill(x, y, x + w, y + h, BG);
        context.fill(x, y, x + w, y + 1, BORDER);
        context.fill(x, y + h - 1, x + w, y + h, BORDER);
        context.fill(x, y, x + 1, y + h, BORDER);
        context.fill(x + w - 1, y, x + w, y + h, BORDER);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_SHINE);

        // Barra de acento na esquerda, clara em cima e escura embaixo.
        int ax = x + 1;
        int aTop = y + 1;
        int aBottom = y + h - 1;
        int aMid = aTop + (aBottom - aTop) / 2;
        context.fill(ax, aTop, ax + ACCENT_W, aMid, ACCENT);
        context.fill(ax, aMid, ax + ACCENT_W, aBottom, ACCENT_DARK);

        int cx = x + 1 + ACCENT_W + 1 + PAD;

        // Logo, alinhado com as duas linhas de texto.
        int logoY = y + 1 + PAD + (fh * 2 + ROW_GAP - LOGO) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, WOLF_LOGO,
                cx, logoY,
                0.0F, 0.0F, LOGO, LOGO, LOGO, LOGO, 0xFFFFFFFF);

        int textX = cx + LOGO + PAD;
        int networkY = y + 1 + PAD;

        // Nome da rede em roxo, com o ponto de conexao a esquerda.
        long now = System.nanoTime() / 1_000_000L;
        boolean live = (now / 500) % 2 == 0;
        int titleDotY = networkY + (fh - DOT) / 2;
        context.fill(textX, titleDotY, textX + DOT, titleDotY + DOT, live ? DOT_ON : DOT_OFF);
        context.drawTextWithShadow(renderer, network, textX + DOT + 4, networkY, TEXT_TITLE);

        // Label e valor do sub-servidor na segunda linha.
        int subY = networkY + fh + ROW_GAP;
        int subDotY = subY + (fh - DOT) / 2;
        context.fill(cx, subDotY, cx + DOT, subDotY + DOT, live ? DOT_ON : DOT_OFF);
        context.drawTextWithShadow(renderer, LABEL, cx + DOT + 4, subY, TEXT_LABEL);

        // O valor alinha com o fim da linha do label, para nao ficar torto.
        int valueX = cx + DOT + 4 + labelW + 6;
        context.drawTextWithShadow(renderer, server, valueX, subY, TEXT_VALUE);
    }
}
