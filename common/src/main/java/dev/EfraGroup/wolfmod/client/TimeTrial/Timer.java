package dev.EfraGroup.wolfmod.client.TimeTrial;

import dev.EfraGroup.wolfmod.client.hud.Hud;
import java.util.Locale;

/**
 * Cronômetro monotônico usado pelo HUD e pelo protocolo de timing.
 *
 * <p>O relógio de parede ({@code currentTimeMillis}) nunca é usado para calcular
 * a duração. O valor observado enquanto o cronômetro está parado continua
 * disponível para que consumidores antigos possam continuar funcionando.</p>
 */
public class Timer {
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final Object LOCK = new Object();

    private static long startAtNanos;
    private static long stopAtNanos;
    private static long durationNanos;
    private static long pausedAtNanos;
    private static long pausedNanos;
    private static boolean paused;
    private static State state = State.IDLE;

    /** Estado explícito do cronômetro, mantendo a API booleana legada. */
    public enum State {
        IDLE,
        RUNNING,
        STOPPED
    }

    /** Inicia (ou reinicia) o cronômetro usando o relógio monotônico. */
    public static void start() {
        startAtNanos(System.nanoTime());
    }

    /** Inicia o cronômetro em um instante monotônico já capturado. */
    public static void startAtNanos(long nanos) {
        synchronized (LOCK) {
            startAtNanos = nanos;
            stopAtNanos = 0L;
            durationNanos = 0L;
            pausedAtNanos = 0L;
            pausedNanos = 0L;
            paused = false;
            state = State.RUNNING;
        }
        updateHud(0L);
    }

    /** Congela o cronômetro no instante atual. */
    public static void stop() {
        stopAtNanos(System.nanoTime());
    }

    /** Congela o cronômetro em um instante monotônico já capturado. */
    public static void stopAtNanos(long nanos) {
        long frozen;
        synchronized (LOCK) {
            if (state != State.RUNNING) {
                frozen = durationNanos;
            } else {
                long effectiveStop = paused ? pausedAtNanos : nanos;
                stopAtNanos = effectiveStop;
                durationNanos = elapsed(startAtNanos + pausedNanos, effectiveStop);
                paused = false;
                pausedAtNanos = 0L;
                pausedNanos = 0L;
                state = State.STOPPED;
                frozen = durationNanos;
            }
        }
        updateHud(frozen);
    }

    /** Limpa o estado e a duração congelada. */
    public static void showDurationMillis(long elapsedMillis) {
        long safeMillis = Math.max(0L, elapsedMillis);
        synchronized (LOCK) {
            durationNanos = safeMillis * NANOS_PER_MILLI;
            state = State.STOPPED;
            paused = false;
            pausedAtNanos = 0L;
            pausedNanos = 0L;
        }
        updateHud(durationNanos);
    }

    public static void reset() {
        synchronized (LOCK) {
            startAtNanos = 0L;
            stopAtNanos = 0L;
            durationNanos = 0L;
            pausedAtNanos = 0L;
            pausedNanos = 0L;
            paused = false;
            state = State.IDLE;
        }
        updateHud(0L);
    }

    /**
     * Pausa a contagem sem alterar o estado público RUNNING. A pausa é
     * removida do tempo efetivamente observado e não exige ticks do jogo.
     */
    public static void pauseAtNanos(long nanos) {
        synchronized (LOCK) {
            if (state != State.RUNNING || paused) {
                return;
            }
            paused = true;
            pausedAtNanos = nanos;
        }
    }

    /** Retoma a contagem depois de uma pausa. */
    public static void resumeAtNanos(long nanos) {
        synchronized (LOCK) {
            if (state != State.RUNNING || !paused) {
                return;
            }
            long pauseLength = nanos - pausedAtNanos;
            if (pauseLength > 0L) {
                pausedNanos += pauseLength;
            }
            paused = false;
            pausedAtNanos = 0L;
        }
    }

    public static boolean isPaused() {
        synchronized (LOCK) {
            return paused;
        }
    }

    /** Atualiza o texto do HUD com o tempo monotônico atual ou congelado. */
    public static void update() {
        updateHud(getElapsedNanos());
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return state == State.RUNNING;
        }
    }

    public static State getState() {
        synchronized (LOCK) {
            return state;
        }
    }

    /** Retorna a duração congelada, ou a duração monotônica enquanto corre. */
    public static long getElapsedNanos() {
        synchronized (LOCK) {
            if (state != State.RUNNING) {
                return durationNanos;
            }
            long now = paused ? pausedAtNanos : System.nanoTime();
            return elapsed(startAtNanos + pausedNanos, now);
        }
    }

    /** API legada em milissegundos, agora baseada em {@link System#nanoTime()}. */
    public static long getElapsedMillis() {
        return getElapsedNanos() / NANOS_PER_MILLI;
    }

    public static long getStartAtNanos() {
        synchronized (LOCK) {
            return startAtNanos;
        }
    }

    public static long getStopAtNanos() {
        synchronized (LOCK) {
            return stopAtNanos;
        }
    }

    public static long getFrozenDurationNanos() {
        synchronized (LOCK) {
            return durationNanos;
        }
    }

    /** Alias legível para integrações que preferem o substantivo da duração. */
    public static long getDurationNanos() {
        return getFrozenDurationNanos();
    }

    private static long elapsed(long from, long to) {
        long value = to - from;
        return value > 0L ? value : 0L;
    }

    private static void updateHud(long elapsedNanos) {
        long totalMillis = elapsedNanos / NANOS_PER_MILLI;
        long minutes = totalMillis / 60_000L;
        long seconds = (totalMillis % 60_000L) / 1_000L;
        long millis = totalMillis % 1_000L;
        Hud.updateTime(String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis));
    }
}
