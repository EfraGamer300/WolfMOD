package dev.EfraGroup.wolfmod.client.TimeTrial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.math.Vec3d;

/**
 * Região imutável usada para detectar a entrada de um veículo numa zona de
 * timing. A projeção de detecção é feita no plano XZ; o eixo Y é mantido nos
 * dados para aceitar os dois formatos de geometria enviados pelo servidor.
 */
public final class TimingRegion {
    private static final double EPSILON = 1.0e-9;

    private final String id;
    private final Role role;
    private final Shape shape;
    private final Vec3d min;
    private final Vec3d max;
    private final Vec3d[] points;

    /** Papel de um cruzamento no protocolo. */
    public enum Role {
        START,
        END;

        public static Role from(String value, Role fallback) {
            if (value == null) {
                return fallback;
            }
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            if ("START".equals(normalized) || "BEGIN".equals(normalized)) {
                return START;
            }
            if ("END".equals(normalized) || "FINISH".equals(normalized)) {
                return END;
            }
            return fallback;
        }
    }

    /** Formato de representação da região. */
    public enum Shape {
        AABB,
        POLY;

        public static Shape from(String value, Shape fallback) {
            if (value == null) {
                return fallback;
            }
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            if ("AABB".equals(normalized) || "BOX".equals(normalized) || "BOUNDS".equals(normalized)) {
                return AABB;
            }
            if ("POLY".equals(normalized) || "POLYGON".equals(normalized)) {
                return POLY;
            }
            return fallback;
        }
    }

    /** Alias para a API anterior, que expunha {@code Type}. */
    public enum Type {
        AABB,
        POLY;

        public Shape asShape() {
            return this == AABB ? Shape.AABB : Shape.POLY;
        }
    }

    public TimingRegion(String id, Type type, Vec3d min, Vec3d max, Vec3d[] vertices) {
        this(id, Role.START, Objects.requireNonNull(type, "type").asShape(), min, max, vertices);
    }

    public TimingRegion(String id, Role role, Shape shape, Vec3d min, Vec3d max, Vec3d[] points) {
        this.id = requireText(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        this.shape = Objects.requireNonNull(shape, "shape");
        if (this.shape == Shape.AABB) {
            if (min == null || max == null) {
                throw new IllegalArgumentException("AABB exige bounds min/max");
            }
            validatePoint(min);
            validatePoint(max);
            this.min = new Vec3d(Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z));
            this.max = new Vec3d(Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z));
            this.points = null;
        } else {
            if (points == null || points.length < 3 || points.length > 64) {
                throw new IllegalArgumentException("POLY exige de 3 a 64 pontos");
            }
            this.points = points.clone();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Vec3d point : this.points) {
                validatePoint(point);
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
            this.min = new Vec3d(minX, minY, minZ);
            this.max = new Vec3d(maxX, maxY, maxZ);
        }
    }

    public static TimingRegion aabb(String id, Vec3d min, Vec3d max) {
        return new TimingRegion(id, Role.START, Shape.AABB, min, max, null);
    }

    public static TimingRegion aabb(String id, Role role, Vec3d min, Vec3d max) {
        return new TimingRegion(id, role, Shape.AABB, min, max, null);
    }

    public static TimingRegion poly(String id, Vec3d... vertices) {
        return new TimingRegion(id, Role.START, Shape.POLY, null, null, vertices);
    }

    public static TimingRegion poly(String id, Role role, Vec3d... vertices) {
        return new TimingRegion(id, role, Shape.POLY, null, null, vertices);
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public Shape shape() {
        return shape;
    }

    /** Compatibilidade com a API anterior baseada em {@link Type}. */
    public Type type() {
        return shape == Shape.AABB ? Type.AABB : Type.POLY;
    }

    public Vec3d min() {
        return min;
    }

    public Vec3d max() {
        return max;
    }

    /** Pontos XZ do polígono, ou {@code null} para AABB. */
    public Vec3d[] points() {
        return points == null ? null : points.clone();
    }

    /** Alias do campo chamado vertices na versão anterior. */
    public Vec3d[] vertices() {
        return points();
    }

    /**
     * Compatibilidade com a API anterior: AABB considera também o eixo Y.
     * A detecção de timing deve chamar {@link #containsXZ(Vec3d)}.
     */
    public boolean contains(Vec3d point) {
        if (point == null) {
            return false;
        }
        if (shape == Shape.AABB) {
            return point.x >= min.x && point.x <= max.x
                    && point.y >= min.y && point.y <= max.y
                    && point.z >= min.z && point.z <= max.z;
        }
        return containsPoly(point.x, point.z);
    }

    /** Teste de plano XZ usado pelo detector de cruzamentos. */
    public boolean containsXZ(Vec3d point) {
        return point != null && containsXZ(point.x, point.z);
    }

    public boolean contains(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return false;
        }
        return shape == Shape.AABB
                ? x >= min.x && x <= max.x && z >= min.z && z <= max.z
                : containsPoly(x, z);
    }

    public boolean containsXZ(double x, double z) {
        return contains(x, z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimingRegion region)) {
            return false;
        }
        return id.equals(region.id)
                && role == region.role
                && shape == region.shape
                && min.equals(region.min)
                && max.equals(region.max)
                && Arrays.equals(points, region.points);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, role, shape, min, max);
        result = 31 * result + Arrays.hashCode(points);
        return result;
    }

    @Override
    public String toString() {
        return "TimingRegion[" + id + ", " + role + ", " + shape
                + ", min=" + min + ", max=" + max + ", points=" + Arrays.toString(points) + "]";
    }

    /**
     * Retorna a fração de entrada na região ao longo de previous→current, ou
     * {@code -1} se o segmento não entrou. A fração é 0..1.
     */
    public double entryFraction(Vec3d previous, Vec3d current) {
        if (previous == null || current == null || containsXZ(previous)) {
            return -1.0;
        }
        double fraction = shape == Shape.AABB
                ? aabbEntryFraction(previous, current)
                : polyEntryFraction(previous, current);
        return fraction < -EPSILON || fraction > 1.0 + EPSILON ? -1.0 : clamp(fraction, 0.0, 1.0);
    }

    private boolean containsPoly(double x, double z) {
        boolean inside = false;
        int count = points.length;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec3d a = points[j];
            Vec3d b = points[i];
            boolean crosses = ((a.z > z) != (b.z > z))
                    && (x < (b.x - a.x) * (z - a.z) / (b.z - a.z) + a.x);
            if (crosses) {
                inside = !inside;
            }
        }
        return inside || onBoundary(x, z);
    }

    private boolean onBoundary(double x, double z) {
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            Vec3d a = points[j];
            Vec3d b = points[i];
            if (Math.abs(cross(a.x, a.z, b.x, b.z, x, z)) <= EPSILON
                    && x >= Math.min(a.x, b.x) - EPSILON
                    && x <= Math.max(a.x, b.x) + EPSILON
                    && z >= Math.min(a.z, b.z) - EPSILON
                    && z <= Math.max(a.z, b.z) + EPSILON) {
                return true;
            }
        }
        return false;
    }

    private double aabbEntryFraction(Vec3d previous, Vec3d current) {
        double[] xRange = clipAxis(previous.x, current.x - previous.x, min.x, max.x);
        double[] zRange = clipAxis(previous.z, current.z - previous.z, min.z, max.z);
        if (xRange == null || zRange == null) {
            return -1.0;
        }
        double tMin = Math.max(xRange[0], zRange[0]);
        double tMax = Math.min(xRange[1], zRange[1]);
        if (tMin > tMax || tMin > 1.0 || tMax < 0.0) {
            return -1.0;
        }
        return tMin;
    }

    /** Retorna o intervalo [entrada, saída] do segmento numa projeção 1D. */
    private static double[] clipAxis(double start, double delta, double lower, double upper) {
        if (Math.abs(delta) <= EPSILON) {
            return start >= lower - EPSILON && start <= upper + EPSILON
                    ? new double[]{0.0, 1.0}
                    : null;
        }
        double first = (lower - start) / delta;
        double second = (upper - start) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        return new double[]{first, second};
    }

    private double polyEntryFraction(Vec3d previous, Vec3d current) {
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0);
        cuts.add(1.0);
        Vec3d delta = current.subtract(previous);
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            addIntersectionParameter(previous, delta, points[j], points[i], cuts);
        }
        Collections.sort(cuts);

        // As interseções dividem o segmento em intervalos. O primeiro
        // intervalo cujo interior está dentro do polígono começa exatamente
        // na fração de entrada. Testar o meio do intervalo também trata o
        // caso de o segmento terminar exatamente na borda.
        for (int i = 0; i + 1 < cuts.size(); i++) {
            double t0 = cuts.get(i);
            double t1 = cuts.get(i + 1);
            if (t1 - t0 <= EPSILON) {
                continue;
            }
            double middle = (t0 + t1) * 0.5;
            if (contains(previous.add(delta.multiply(middle)))) {
                return clamp(t0, 0.0, 1.0);
            }
        }

        // Uma entrada exatamente no endpoint não possui intervalo posterior;
        // o ponto final é suficiente para identificá-la.
        return containsXZ(current) ? 1.0 : -1.0;
    }

    private static void addIntersectionParameter(Vec3d origin, Vec3d delta, Vec3d a, Vec3d b, List<Double> cuts) {
        double rx = delta.x;
        double rz = delta.z;
        double sx = b.x - a.x;
        double sz = b.z - a.z;
        double denominator = cross(0.0, 0.0, rx, rz, sx, sz);
        if (Math.abs(denominator) > EPSILON) {
            double qpx = a.x - origin.x;
            double qpz = a.z - origin.z;
            double t = cross(0.0, 0.0, qpx, qpz, sx, sz) / denominator;
            double u = cross(0.0, 0.0, qpx, qpz, rx, rz) / denominator;
            if (t >= -EPSILON && t <= 1.0 + EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON) {
                cuts.add(clamp(t, 0.0, 1.0));
            }
            return;
        }
        if (Math.abs(cross(0.0, 0.0, a.x - origin.x, a.z - origin.z, rx, rz)) > EPSILON
                || Math.abs(rx) <= EPSILON) {
            return;
        }
        addProjection(origin, delta, a, cuts);
        addProjection(origin, delta, b, cuts);
    }

    private static void addProjection(Vec3d origin, Vec3d delta, Vec3d point, List<Double> cuts) {
        double denominator = delta.x * delta.x + delta.z * delta.z;
        if (denominator <= EPSILON) {
            return;
        }
        double t = ((point.x - origin.x) * delta.x + (point.z - origin.z) * delta.z) / denominator;
        if (t >= -EPSILON && t <= 1.0 + EPSILON) {
            cuts.add(clamp(t, 0.0, 1.0));
        }
    }

    private static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    private static void validatePoint(Vec3d point) {
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("Região contém ponto inválido");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " não pode ficar vazio");
        }
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
