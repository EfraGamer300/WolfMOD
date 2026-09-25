package dev.EfraGroup.wolfmod.client.hud;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class Hud {
    private static String displayTime = "00.000";

    private static final int BG = 0xE6121220;
    private static final int BORDER = 0xFF2E2847;
    private static final int TOP_SHINE = 0x24FFFFFF;
    private static final int ACCENT = 0xFFA020F0;
    private static final int ACCENT_DARK = 0xFF5B0BA6;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_LABEL = 0xFF9A93B8;
    private static final int TEXT_MILLIS = 0xFFC084FC;
    private static final int LIVE_ON = 0xFFFF3B3B;
    private static final int LIVE_OFF = 0xFF5A1A1A;
    private static final int SWEEP = 0x66A020F0;
    private static final int SHADOW = 0x50000000;

    private static final String LABEL = "TEMPO DE VOLTA";
    private static final int PAD_X = 10;
    private static final int PAD_TOP = 7;
    private static final int PAD_BOTTOM = 8;
    private static final int ROW_GAP = 3;
    private static final int ACCENT_W = 3;
    private static final int DOT = 5;

    public static void updateTime(String time) {
        if (time.startsWith("00:")) {
            displayTime = time.substring(3);
        } else {
            displayTime = time;
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.options.hudHidden || !Timer.isRunning()) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        int fontHeight = renderer.fontHeight;

        int dot = displayTime.lastIndexOf('.');
        String main = dot >= 0 ? displayTime.substring(0, dot) : displayTime;
        String millis = dot >= 0 ? displayTime.substring(dot) : "";

        int mainW = renderer.getWidth(main) + 1;
        int millisW = renderer.getWidth(millis);
        int timeW = mainW + millisW;

        int labelW = renderer.getWidth(LABEL);
        int labelRowW = DOT + 4 + labelW;

        int contentW = Math.max(labelRowW, timeW);
        int extras = 1 + ACCENT_W + 1 + PAD_X + PAD_X + 1;
        int w = contentW + extras;
        int h = 1 + PAD_TOP + fontHeight + ROW_GAP + fontHeight + PAD_BOTTOM + 1;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int x = (screenWidth / 2) - (w / 2);
        int y = screenHeight - h - 48;

        context.fill(x - 2, y - 2, x + w + 2, y + h + 2, SHADOW);
        context.fill(x, y, x + w, y + h, BG);

        context.fill(x, y, x + w, y + 1, BORDER);
        context.fill(x, y + h - 1, x + w, y + h, BORDER);
        context.fill(x, y, x + 1, y + h, BORDER);
        context.fill(x + w - 1, y, x + w, y + h, BORDER);

        context.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_SHINE);

        int accentX = x + 1;
        int accentTop = y + 1;
        int accentBottom = y + h - 1;
        int accentMid = accentTop + (accentBottom - accentTop) / 2;
        context.fill(accentX, accentTop, accentX + ACCENT_W, accentMid, ACCENT);
        context.fill(accentX, accentMid, accentX + ACCENT_W, accentBottom, ACCENT_DARK);

        // A animação é visual; o tempo do cronômetro continua monotônico no Timer.
        long now = System.nanoTime() / 1_000_000L;
        int sweepLen = 14;
        int sweepRange = Math.max(1, w - 2 - sweepLen);
        int sweepX = x + 1 + (int) ((now / 6) % sweepRange);
        context.fill(sweepX, y + h - 3, sweepX + sweepLen, y + h - 2, SWEEP);

        int contentX = x + 1 + ACCENT_W + 1 + PAD_X;

        boolean live = (now / 500) % 2 == 0;
        int labelY = y + 1 + PAD_TOP;
        int dotY = labelY + (fontHeight - DOT) / 2;
        context.fill(contentX, dotY, contentX + DOT, dotY + DOT, live ? LIVE_ON : LIVE_OFF);
        context.drawText(renderer, LABEL, contentX + DOT + 4, labelY, TEXT_LABEL, true);

        int timeY = labelY + fontHeight + ROW_GAP;
        int timeX = contentX + (contentW - timeW) / 2;

        context.drawText(renderer, main, timeX, timeY, TEXT_WHITE, true);
        context.drawText(renderer, main, timeX + 1, timeY, TEXT_WHITE, false);
        if (!millis.isEmpty()) {
            context.drawText(renderer, millis, timeX + mainW, timeY, TEXT_MILLIS, true);
        }
    }
}
