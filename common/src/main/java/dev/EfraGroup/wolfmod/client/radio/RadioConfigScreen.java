package dev.EfraGroup.wolfmod.client.radio;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;

public class RadioConfigScreen extends Screen {
    private static final int PW = 340;
    private static final int PH = 280;
    private static final int BTN_H = 20;
    private static final int COL1 = 115;

    private List<String> mixers;
    private int mixerIdx = -1;
    private double threshold;
    private boolean monitoring = false;

    public RadioConfigScreen() {
        super(Text.literal("Configurar Radio"));
    }

    @Override
    protected void init() {
        RadioSettings.load();
        threshold = RadioSettings.getThreshold();
        mixers = RadioManager.getAvailableMixers();
        String sel = RadioSettings.getSelectedMixer();
        mixerIdx = sel.isEmpty() ? -1 : mixers.indexOf(sel);

        int cx = width / 2;
        int pl = cx - PW / 2;
        int top = height / 2 - PH / 2;

        // Microfone buttons
        int y1 = top + 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> cycleMic(-1))
                .dimensions(pl + COL1 + 80, y1, 28, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> cycleMic(1))
                .dimensions(pl + COL1 + 170, y1, 28, BTN_H).build());

        // Threshold buttons
        int y2 = y1 + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("--"), b -> adjThreshold(-10))
                .dimensions(pl + COL1 + 80, y2, 34, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjThreshold(-1))
                .dimensions(pl + COL1 + 118, y2, 24, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjThreshold(1))
                .dimensions(pl + COL1 + 192, y2, 24, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("++"), b -> adjThreshold(10))
                .dimensions(pl + COL1 + 220, y2, 34, BTN_H).build());

        // Monitor button
        int y3 = y2 + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("[ Monitorar ]"), b -> toggleMonitor())
                .dimensions(cx - 48, y3, 96, BTN_H).build());

        // Noise suppression toggle
        int y4 = y3 + 30;
        boolean nsEnabled = RadioSettings.isNoiseSuppressionEnabled();
        addDrawableChild(ButtonWidget.builder(
                Text.literal(nsEnabled ? "Supressao de Ruido: §aON" : "Supressao de Ruido: §cOFF"),
                b -> toggleNoiseSuppression())
                .dimensions(cx - 75, y4, 150, BTN_H).build());

        // Sidetone toggle
        int y5 = y4 + 24;
        boolean stEnabled = RadioSettings.isSidetoneEnabled();
        addDrawableChild(ButtonWidget.builder(
                Text.literal(stEnabled ? "Audio Monitorado: §aON" : "Audio Monitorado: §cOFF"),
                b -> toggleSidetone())
                .dimensions(cx - 75, y5, 150, BTN_H).build());

        // Sidetone volume buttons
        int y6 = y5 + 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Vol -"), b -> adjSidetoneVol(-0.1))
                .dimensions(pl + COL1 + 80, y6, 44, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Vol +"), b -> adjSidetoneVol(0.1))
                .dimensions(pl + COL1 + 170, y6, 44, BTN_H).build());

        // Close button
        int y7 = y6 + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Fechar"), b -> close())
                .dimensions(cx - 35, y7, 70, BTN_H).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        int cx = width / 2;
        int pl = cx - PW / 2;
        int top = height / 2 - PH / 2;

        // Panel background
        ctx.fill(pl, top, pl + PW, top + PH, 0xCC111111);
        ctx.fill(pl, top, pl + PW, top + 2, 0xFF4488FF);

        // Title
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, title, cx - tw / 2, top + 10, 0xFF8888FF, false);

        int y1 = top + 32;
        int y2 = y1 + 30;
        int y3 = y2 + 30;
        int y6 = y3 + 30 + 24 + 24 + 24;

        // Microfone
        ctx.drawText(textRenderer, Text.literal("Microfone"), pl + 12, y1 + 5, 0xFF8888FF, false);
        String micName;
        if (mixerIdx >= 0 && mixerIdx < mixers.size()) {
            micName = mixers.get(mixerIdx);
            if (micName.length() > 26) micName = micName.substring(0, 24) + "..";
        } else {
            micName = "Padrao do sistema";
        }
        int micW = textRenderer.getWidth(micName);
        ctx.drawText(textRenderer, Text.literal(micName), pl + COL1 + 145 - micW / 2, y1 + 5, 0xFFFFFFFF, false);

        // Threshold
        ctx.drawText(textRenderer, Text.literal("Threshold"), pl + 12, y2 + 5, 0xFF8888FF, false);
        String thStr = String.format("%.0f", threshold);
        int thW = textRenderer.getWidth(thStr);
        ctx.drawText(textRenderer, Text.literal(thStr), pl + COL1 + 165 - thW / 2, y2 + 5, 0xFFFFFF44, false);

        // Live monitor section
        if (monitoring) {
            double amp = RadioManager.getMonitorAmplitude();

            ctx.drawText(textRenderer, Text.literal("Amplitude:"), pl + 12, y3 + 5, 0xFF8888FF, false);
            String ampStr = String.format("%.0f", amp);
            int ampColor = amp >= threshold ? 0xFF44FF44 : (amp > threshold * 0.5f ? 0xFFFFFF44 : 0xFFFF4444);
            ctx.drawText(textRenderer, Text.literal(ampStr), pl + COL1 + 10, y3 + 5, ampColor, false);

            // Bar
            int barX = pl + 12;
            int barY = y3 + 28;
            int barW = PW - 24;
            int barH = 14;
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xCC333333);

            int fillW = (int) (barW * Math.min(1.0, amp / 500.0));
            if (fillW > 0) {
                ctx.fill(barX, barY, barX + Math.min(barW, fillW), barY + barH, ampColor);
            }

            int thX = barX + (int) (barW * Math.min(1.0, threshold / 500.0));
            ctx.fill(thX, barY - 1, thX + 2, barY + barH + 1, 0xFFFFFFFF);

            String status;
            int statusColor;
            if (amp >= threshold) {
                status = "OK - Falando";
                statusColor = 0xFF44FF44;
            } else if (amp > 0) {
                status = "Fale mais alto ou abaixe o threshold";
                statusColor = 0x66FFFFFF;
            } else {
                status = "Fale algo no microfone...";
                statusColor = 0x44FFFFFF;
            }
            ctx.drawText(textRenderer, Text.literal(status), barX, barY + barH + 3, statusColor, false);
        } else {
            ctx.drawText(textRenderer, Text.literal("Clique em [ Monitorar ] para testar"), pl + 12, y3 + 5, 0x66FFFFFF, false);
        }

        // Volume label
        ctx.drawText(textRenderer, Text.literal("Vol Monitor: " + String.format("%.0f%%", RadioSettings.getSidetoneVolume() * 100)), pl + COL1 + 130 - textRenderer.getWidth("Vol Monitor: 100%") / 2 + 15, y6 + 5, 0xFFFFFF44, false);

        ctx.drawText(textRenderer, Text.literal("ESC fecha"), cx - textRenderer.getWidth("ESC fecha") / 2, top + PH - 14, 0x44FFFFFF, false);

        super.render(ctx, mx, my, delta);
    }

    private void cycleMic(int dir) {
        if (mixers.isEmpty()) return;
        if (mixerIdx < 0) mixerIdx = dir > 0 ? 0 : mixers.size() - 1;
        else mixerIdx = (mixerIdx + dir + mixers.size()) % mixers.size();
        RadioSettings.setSelectedMixer(mixers.get(mixerIdx));
    }

    private void adjThreshold(double delta) {
        threshold = Math.max(1, Math.min(1000, threshold + delta));
        RadioSettings.setThreshold(threshold);
    }

    private void toggleMonitor() {
        if (monitoring) {
            RadioManager.stopMonitor();
            monitoring = false;
        } else {
            RadioManager.startMonitor();
            monitoring = true;
        }
    }

    private void toggleNoiseSuppression() {
        boolean current = RadioSettings.isNoiseSuppressionEnabled();
        RadioSettings.setNoiseSuppressionEnabled(!current);
        this.clearChildren();
        this.init();
    }

    private void toggleSidetone() {
        boolean current = RadioSettings.isSidetoneEnabled();
        RadioSettings.setSidetoneEnabled(!current);
        this.clearChildren();
        this.init();
    }

    private void adjSidetoneVol(double delta) {
        double vol = RadioSettings.getSidetoneVolume();
        RadioSettings.setSidetoneVolume(Math.round((vol + delta) * 10.0) / 10.0);
        this.clearChildren();
        this.init();
    }

    @Override
    public void close() {
        if (monitoring) {
            RadioManager.stopMonitor();
            monitoring = false;
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
