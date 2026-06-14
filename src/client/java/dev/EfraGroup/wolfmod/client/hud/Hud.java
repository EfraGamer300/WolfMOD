package dev.EfraGroup.wolfmod.client.hud;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class Hud {
    private static String displayTime = "00.000";

    private static final int HEIGHT = 16;
    private static final int BACKGROUND_COLOR = 0xCC000000;
    private static final int WOLF_BLUE = 0xFF0000FF; // Adicionado Alpha
    private static final int WOLF_WHITE = 0xFFFFFFFF;

    public static void updateTime(String time) {
        if (time.startsWith("00:")) {
            displayTime = time.substring(3);
        } else {
            displayTime = time;
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // SÓ RENDERIZA SE: O player existir, a HUD não estiver oculta E o Timer estiver rodando
        if (client.player == null || client.options.hudHidden || !Timer.isRunning()) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int textWidth = renderer.getWidth(displayTime);
        int dynamicWidth = textWidth + 20;

        int x = (screenWidth / 2) - (dynamicWidth / 2);
        int y = screenHeight - 55;

        // 1. Sombra externa
        context.fill(x - 1, y - 1, x + dynamicWidth + 1, y + HEIGHT + 1, 0x55000000);

        // 2. Fundo Principal
        context.fill(x, y, x + dynamicWidth, y + HEIGHT, BACKGROUND_COLOR);

        // 3. Detalhe lateral
        context.fill(x, y, x + 2, y + HEIGHT, WOLF_BLUE);

        // 4. Texto do Cronômetro
        int textX = x + (dynamicWidth / 2) - (textWidth / 2) + 1;
        int textY = y + (HEIGHT / 2) - (renderer.fontHeight / 2);
        context.drawText(renderer, displayTime, textX, textY, WOLF_WHITE, false);

        // 5. Brilho no topo
        context.fill(x + 2, y, x + dynamicWidth, y + 1, 0x33FFFFFF);
    }
}