package dev.EfraGroup.wolfmod.client.TimeTrial;

import dev.EfraGroup.wolfmod.client.hud.Hud;

public class Timer {
    private static long startTime = 0;
    private static boolean running = false;

    public static void start() {
        startTime = System.currentTimeMillis();
        running = true;
    }

    public static void stop() {
        running = false;
    }

    public static void reset() {
        startTime = 0;
        running = false;
        Hud.updateTime("00:00.000");
    }

    public static void update() {
        if (!running) return;

        long elapsed = System.currentTimeMillis() - startTime;

        long minutes = (elapsed / 60000);
        long seconds = (elapsed % 60000) / 1000;
        long millis = elapsed % 1000;

        String formatted = String.format("%02d:%02d.%03d", minutes, seconds, millis);
        Hud.updateTime(formatted);
    }

    public static boolean isRunning() {
        return running;
    }

    public static long getElapsedMillis() {
        if (!running) {
            return 0L;
        }

        return System.currentTimeMillis() - startTime;
    }
}
