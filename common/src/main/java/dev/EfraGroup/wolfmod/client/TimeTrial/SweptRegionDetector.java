package dev.EfraGroup.wolfmod.client.TimeTrial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Detector local de cruzamentos.
 *
 * <p>O detector só observa o veículo do jogador. Entre dois ticks ele testa o
 * segmento entre a posição anterior e a atual, em vez de testar apenas a
 * posição atual. O instante do cruzamento é interpolado com
 * {@link System#nanoTime()} e pode ser aplicado diretamente ao Timer.</p>
 */
public final class SweptRegionDetector {
    public static final double MAX_TELEPORT_DISTANCE = 2_500.0;
    private static final double MAX_TELEPORT_DISTANCE_SQUARED =
            MAX_TELEPORT_DISTANCE * MAX_TELEPORT_DISTANCE;
    /**
     * Start and finish usually share the same line, so one crossing enters both regions.
     * The server ignores a START/END pair inside this window; without the same rule the
     * client started and stopped the lap in the same tick, leaving the HUD dead.
     */
    private static final long START_END_DEBOUNCE_NANOS = 2_000_000_000L;

    private TimingGeometry geometry;
    private ClientWorld world;
    private Vec3d previousPosition;
    private long previousNanos;
    private boolean hasSample;
    private boolean paused;
    private boolean startObserved;
    private boolean endObserved;
    private long startAtNanos;
    /** Checkpoint indices in lap order, rebuilt with the geometry. */
    private final List<Integer> checkpointOrder = new ArrayList<>();
    private int nextCheckpointSlot;
    private final Set<String> insideRegions = new HashSet<>();

    /** Motivo pelo qual uma amostra não pode produzir cruzamentos. */
    public enum Status {
        OK,
        NO_GEOMETRY,
        PAUSED,
        NOT_DRIVING,
        DISCONNECTED,
        WORLD_CHANGED,
        TELEPORT
    }

    /** Cruzamento local, já interpolado no tempo monotônico. */
    public record Crossing(
            TimingRegion region,
            double fraction,
            long atNanos,
            Vec3d previousPosition,
            Vec3d currentPosition
    ) {
        public Crossing {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(previousPosition, "previousPosition");
            Objects.requireNonNull(currentPosition, "currentPosition");
        }
    }

    /** Resultado de uma amostra, incluindo todos os cruzamentos do segmento. */
    public record TickResult(Status status, List<Crossing> crossings) {
        public TickResult {
            Objects.requireNonNull(status, "status");
            crossings = crossings == null ? List.of() : List.copyOf(crossings);
        }

        public boolean hasCrossings() {
            return !crossings.isEmpty();
        }
    }

    public SweptRegionDetector() {
    }

    public TimingGeometry geometry() {
        return geometry;
    }

    public void setGeometry(TimingGeometry geometry) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        checkpointOrder.clear();
        checkpointOrder.addAll(geometry.checkpointIndices());
        nextCheckpointSlot = 0;
        clearSample();
        startObserved = false;
        endObserved = false;
    }

    /** Remove a geometria e zera qualquer cruzamento observado. */
    public void disarm() {
        geometry = null;
        world = null;
        checkpointOrder.clear();
        nextCheckpointSlot = 0;
        clearSample();
        startObserved = false;
        endObserved = false;
    }

    /**
     * Zera a sequência de amostras, mas conserva a geometria. É útil ao
     * detectar uma pausa, teleporte ou mudança de mundo sem apagar o
     * protocolo ainda.
     */
    public void resetTracking() {
        clearSample();
    }

    /** Zera o estado de uma tentativa de volta, mantendo a geometria. */
    public void resetRun() {
        clearSample();
        startObserved = false;
        endObserved = false;
        nextCheckpointSlot = 0;
    }

    /**
     * Declares the current lap already started without a sample crossing it. Used when the
     * server loops a circuit at the finish: the next boundary is the finish, and the detector
     * must accept it as such (an END without an observed START is ignored).
     */
    public void markRunStarted(long atNanos) {
        startObserved = true;
        endObserved = false;
        startAtNanos = atNanos;
        nextCheckpointSlot = 0;
    }

    public boolean allCheckpointsPassed() {
        return nextCheckpointSlot >= checkpointOrder.size();
    }

    public int checkpointCount() {
        return checkpointOrder.size();
    }

    public int passedCheckpoints() {
        return Math.min(nextCheckpointSlot, checkpointOrder.size());
    }

    /** Next checkpoint index expected, or -1 when there is nothing left. */
    public int expectedCheckpointIndex() {
        return allCheckpointsPassed() ? -1 : checkpointOrder.get(nextCheckpointSlot);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean hasSample() {
        return hasSample;
    }

    public Vec3d previousPosition() {
        return previousPosition;
    }

    public long previousNanos() {
        return previousNanos;
    }

    public boolean startObserved() {
        return startObserved;
    }

    public boolean endObserved() {
        return endObserved;
    }

    /** Amostra usando o relógio monotônico do processo. */
    public TickResult tick(MinecraftClient client) {
        return tick(client, System.nanoTime());
    }

    /**
     * Processa uma posição atual. O timestamp deve vir de
     * {@link System#nanoTime()} e ser capturado imediatamente antes da
     * amostra, no mesmo thread do cliente.
     */
    public TickResult tick(MinecraftClient client, long nowNanos) {
        if (geometry == null || geometry.isEmpty()) {
            clearSample();
            return new TickResult(Status.NO_GEOMETRY, List.of());
        }
        if (client == null || client.world == null || client.player == null
                || client.getNetworkHandler() == null) {
            clearSample();
            return new TickResult(Status.DISCONNECTED, List.of());
        }

        ClientWorld currentWorld = client.world;
        if (world == null) {
            world = currentWorld;
        } else if (world != currentWorld) {
            world = currentWorld;
            clearSample();
            return new TickResult(Status.WORLD_CHANGED, List.of());
        }

        if (!(client.player.getVehicle() instanceof AbstractBoatEntity boat)) {
            clearSample();
            return new TickResult(Status.NOT_DRIVING, List.of());
        }

        Vec3d current = boat.getPos();
        if (!finite(current)) {
            clearSample();
            return new TickResult(Status.TELEPORT, List.of());
        }

        if (client.isPaused()) {
            paused = true;
            return new TickResult(Status.PAUSED, List.of());
        }

        // Não atravesse o intervalo coberto pela pausa: na retomada a nova
        // posição vira a primeira amostra, evitando um falso cruzamento.
        if (paused) {
            paused = false;
            setSample(current, nowNanos);
            updateInside(current);
            return new TickResult(Status.OK, List.of());
        }

        if (!hasSample) {
            setSample(current, nowNanos);
            updateInside(current);
            return new TickResult(Status.OK, List.of());
        }

        if (previousPosition.squaredDistanceTo(current) > MAX_TELEPORT_DISTANCE_SQUARED) {
            setSample(current, nowNanos);
            updateInside(current);
            return new TickResult(Status.TELEPORT, List.of());
        }

        Vec3d before = previousPosition;
        long beforeNanos = previousNanos;
        List<Crossing> crossings = detect(before, current, beforeNanos, nowNanos);
        setSample(current, nowNanos);
        updateInside(current);
        return new TickResult(Status.OK, crossings);
    }

    private List<Crossing> detect(Vec3d before, Vec3d after, long beforeNanos, long afterNanos) {
        List<Crossing> candidates = new ArrayList<>();
        for (TimingRegion region : geometry.regions()) {
            if (insideRegions.contains(region.id())) {
                continue;
            }
            double fraction = region.entryFraction(before, after);
            if (fraction < 0.0) {
                continue;
            }
            // The analytic entry fraction is exact for AABB/POLY; the bisection narrows it
            // further to sub-millisecond resolution (8 halvings around the analytic value).
            fraction = refineEntryFraction(region, before, after, fraction);
            candidates.add(new Crossing(
                    region,
                    fraction,
                    interpolateNanos(beforeNanos, afterNanos, fraction),
                    before,
                    after
            ));
        }
        candidates.sort(Comparator.comparingDouble(Crossing::fraction)
                .thenComparingLong(Crossing::atNanos));

        List<Crossing> accepted = new ArrayList<>();
        for (Crossing crossing : candidates) {
            TimingRegion.Role role = crossing.region().role();
            // Guard regions (LAGSTART/LAGEND) are validated by the server. Accepting them
            // here would close a lap on a region that is not the finish line.
            if (role == TimingRegion.Role.GUARD) {
                continue;
            }
            if (role == TimingRegion.Role.START) {
                if (startObserved) {
                    continue;
                }
                startObserved = true;
                startAtNanos = crossing.atNanos();
                accepted.add(crossing);
            } else if (role == TimingRegion.Role.CHECKPOINT) {
                // Ordered checkpoints: only the next expected index counts, and a crossing
                // out of order (future or already passed) is ignored.
                if (!startObserved || endObserved || allCheckpointsPassed()) {
                    continue;
                }
                if (crossing.region().index() != checkpointOrder.get(nextCheckpointSlot)) {
                    continue;
                }
                nextCheckpointSlot++;
                accepted.add(crossing);
            } else if (startObserved && !endObserved && allCheckpointsPassed()) {
                if (crossing.atNanos() - startAtNanos < START_END_DEBOUNCE_NANOS) {
                    continue;
                }
                endObserved = true;
                accepted.add(crossing);
            }
        }
        return accepted;
    }

    /**
     * Refines the entry fraction with 8 bisection steps (BoatLabs-style sub-tick precision)
     * around the analytic value, so the crossing instant is pinned to ~1/65536 of a tick
     * instead of the raw fraction.
     */
    private static double refineEntryFraction(
            TimingRegion region,
            Vec3d before,
            Vec3d after,
            double approximate
    ) {
        final int iterations = 8;
        final double halfWindow = 1.0 / 256.0;
        double inside = clamp(approximate + halfWindow, 0.0, 1.0);
        double outside = clamp(approximate - halfWindow, 0.0, 1.0);
        for (int i = 0; i < iterations; i++) {
            double middle = (inside + outside) * 0.5;
            if (region.containsXZ(lerp(before, after, middle))) {
                inside = middle;
            } else {
                outside = middle;
            }
        }
        return (inside + outside) * 0.5;
    }

    private static Vec3d lerp(Vec3d before, Vec3d after, double fraction) {
        return new Vec3d(
                before.x + (after.x - before.x) * fraction,
                before.y + (after.y - before.y) * fraction,
                before.z + (after.z - before.z) * fraction
        );
    }

    public void invalidateRun() {
        clearSample();
        paused = false;
        startObserved = false;
        endObserved = false;
        nextCheckpointSlot = 0;
    }

    private void updateInside(Vec3d position) {
        insideRegions.clear();
        for (TimingRegion region : geometry.regions()) {
            if (region.containsXZ(position)) {
                insideRegions.add(region.id());
            }
        }
    }

    private void setSample(Vec3d position, long nanos) {
        previousPosition = position;
        previousNanos = nanos;
        hasSample = true;
    }

    private void clearSample() {
        previousPosition = null;
        previousNanos = 0L;
        hasSample = false;
        insideRegions.clear();
    }

    private static long interpolateNanos(long before, long after, double fraction) {
        long span = after - before;
        if (span <= 0L) {
            return after;
        }
        double value = before + span * clamp(fraction, 0.0, 1.0);
        long rounded = Math.round(value);
        return Math.max(before, Math.min(after, rounded));
    }

    private static boolean finite(Vec3d point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
