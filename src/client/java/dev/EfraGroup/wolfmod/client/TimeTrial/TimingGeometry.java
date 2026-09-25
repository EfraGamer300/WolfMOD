package dev.EfraGroup.wolfmod.client.TimeTrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.EfraGroup.wolfmod.client.TimeTrial.TimingRegion.Role;
import dev.EfraGroup.wolfmod.client.TimeTrial.TimingRegion.Shape;
import dev.EfraGroup.wolfmod.client.TimeTrial.TimingRegion.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.math.Vec3d;

/** Geometria imutável e identificada usada pelo detector do cliente. */
public final class TimingGeometry {
    private final String geometryId;
    private final int version;
    private final Map<String, TimingRegion> regions;

    public TimingGeometry(String geometryId, int version, Collection<TimingRegion> regions) {
        this.geometryId = requireText(geometryId, "geometryId");
        this.version = version;

        Map<String, TimingRegion> copy = new LinkedHashMap<>();
        if (regions != null) {
            for (TimingRegion region : regions) {
                Objects.requireNonNull(region, "region");
                if (copy.putIfAbsent(region.id(), region) != null) {
                    throw new IllegalArgumentException("Região duplicada: " + region.id());
                }
            }
        }
        this.regions = Collections.unmodifiableMap(copy);
    }

    /**
     * Lê o formato JSON do handshake. O parser aceita tanto o formato
     * normalizado ({@code regions:[...]}) quanto os atalhos {@code start}/{@code end}
     * e {@code startRegions}/{@code endRegions}, para não acoplar o cliente a uma
     * única serialização do servidor.
     */
    public static TimingGeometry fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Geometry JSON vazio");
        }
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Geometry deve ser um objeto JSON");
        }
        JsonObject root = rootElement.getAsJsonObject();
        String id = text(root, "geometryId");
        if (id == null) {
            id = text(root, "id");
        }
        int version = integer(root, "version", 1);
        List<TimingRegion> parsed = new ArrayList<>();

        addRegionArray(root, "regions", null, parsed);
        addRegionArray(root, "zones", null, parsed);
        addRegionMap(root, "regions", parsed);
        addRegionArray(root, "startRegions", Role.START, parsed);
        addRegionArray(root, "endRegions", Role.END, parsed);
        addSingleRegion(root, "start", Role.START, parsed);
        addSingleRegion(root, "end", Role.END, parsed);
        addSingleRegion(root, "finish", Role.END, parsed);
        addSingleRegion(root, "startRegion", Role.START, parsed);
        addSingleRegion(root, "endRegion", Role.END, parsed);
        addSingleRegion(root, "START", Role.START, parsed);
        addSingleRegion(root, "END", Role.END, parsed);

        JsonElement geometryElement = root.get("geometry");
        if (geometryElement != null && geometryElement.isJsonObject()) {
            return fromJson(geometryElement.getAsJsonObject().toString());
        }

        if (parsed.isEmpty() && root.has("bounds")) {
            TimingRegion start = parseRegion(root, Role.START, 0, "start");
            parsed.add(start);
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Geometry não contém regiões");
        }
        if (id == null) {
            id = "geometry";
        }
        return new TimingGeometry(id, version, parsed);
    }

    public String geometryId() {
        return geometryId;
    }

    public int version() {
        return version;
    }

    public TimingRegion region(String id) {
        return id == null ? null : regions.get(id);
    }

    public List<TimingRegion> regions() {
        return List.copyOf(regions.values());
    }

    public List<String> regionIds() {
        return List.copyOf(regions.keySet());
    }

    public List<String> idsOfType(Type type) {
        Objects.requireNonNull(type, "type");
        List<String> result = new ArrayList<>();
        for (TimingRegion region : regions.values()) {
            if (region.type() == type) {
                result.add(region.id());
            }
        }
        return List.copyOf(result);
    }

    public List<TimingRegion> regionsOfRole(Role role) {
        Objects.requireNonNull(role, "role");
        List<TimingRegion> result = new ArrayList<>();
        for (TimingRegion region : regions.values()) {
            if (region.role() == role) {
                result.add(region);
            }
        }
        return List.copyOf(result);
    }

    public List<TimingRegion> startRegions() {
        return regionsOfRole(Role.START);
    }

    public List<TimingRegion> endRegions() {
        return regionsOfRole(Role.END);
    }

    public boolean isEmpty() {
        return regions.isEmpty();
    }

    public int size() {
        return regions.size();
    }

    private static void addRegionArray(JsonObject root, String field, Role fallbackRole, List<TimingRegion> out) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonArray()) {
            return;
        }
        int index = 0;
        for (JsonElement element : value.getAsJsonArray()) {
            if (element.isJsonObject()) {
                out.add(parseRegion(element.getAsJsonObject(), fallbackRole, index++, field));
            }
        }
    }

    /** Também aceita regions como objeto: { "start": {...}, "end": {...} }. */
    private static void addRegionMap(JsonObject root, String field, List<TimingRegion> out) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonObject()) {
                JsonObject region = entry.getValue().getAsJsonObject();
                if (!region.has("id") && !region.has("name")) {
                    region.addProperty("id", entry.getKey());
                }
                Role role = Role.from(entry.getKey(), null);
                out.add(parseRegion(region, role, out.size(), field));
            }
        }
    }

    private static void addSingleRegion(JsonObject root, String field, Role fallbackRole, List<TimingRegion> out) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonObject()) {
            return;
        }
        out.add(parseRegion(value.getAsJsonObject(), fallbackRole, 0, field));
    }

    private static TimingRegion parseRegion(JsonObject object, Role fallbackRole, int index, String field) {
        String id = text(object, "id");
        if (id == null) {
            id = text(object, "name");
        }
        if (id == null) {
            id = fallbackRole == null ? field + index : field + "_" + fallbackRole.name().toLowerCase(java.util.Locale.ROOT);
        }

        Role role = Role.from(text(object, "role"), fallbackRole);
        if (role == null) {
            String typeText = text(object, "type");
            role = Role.from(typeText, null);
        }
        if (role == null) {
            role = Role.from(text(object, "kind"), Role.START);
        }

        Shape shape = Shape.from(text(object, "shape"), null);
        if (shape == null) {
            shape = Shape.from(text(object, "form"), null);
        }
        if (shape == null) {
            shape = Shape.from(text(object, "type"), null);
        }
        if (shape == null) {
            shape = object.has("points") || object.has("vertices") || object.has("polygon")
                    ? Shape.POLY : Shape.AABB;
        }

        double[] bounds = parseBounds(object);
        Vec3d min = bounds == null ? null : new Vec3d(bounds[0], bounds[1], bounds[2]);
        Vec3d max = bounds == null ? null : new Vec3d(bounds[3], bounds[4], bounds[5]);
        Vec3d[] points = parsePoints(object);
        if (shape == Shape.AABB && min == null && points != null && points.length > 0) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Vec3d point : points) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
            min = new Vec3d(minX, minY, minZ);
            max = new Vec3d(maxX, maxY, maxZ);
        }
        if (shape == Shape.AABB && min == null) {
            throw new IllegalArgumentException("Região AABB sem bounds: " + id);
        }
        if (shape == Shape.POLY && (points == null || points.length < 3)) {
            throw new IllegalArgumentException("Região POLY sem pontos: " + id);
        }
        return new TimingRegion(id, role, shape, min, max, points);
    }

    private static double[] parseBounds(JsonObject object) {
        JsonElement boundsElement = object.get("bounds");
        if (boundsElement != null && boundsElement.isJsonArray()) {
            double[] values = numbers(boundsElement.getAsJsonArray());
            if (values.length < 4) {
                return null;
            }
            // Aceita [minX, minZ, maxX, maxZ] e a extensão 3D
            // [minX, minY, minZ, maxX, maxY, maxZ].
            if (values.length >= 6) {
                return new double[]{values[0], values[1], values[2], values[3], values[4], values[5]};
            }
            return new double[]{values[0], 0.0, values[1], values[2], 0.0, values[3]};
        }

        JsonObject bounds = boundsElement != null && boundsElement.isJsonObject()
                ? boundsElement.getAsJsonObject() : object;
        double minX = number(bounds, "minX", Double.NaN);
        double minY = number(bounds, "minY", 0.0);
        double minZ = number(bounds, "minZ", Double.NaN);
        double maxX = number(bounds, "maxX", Double.NaN);
        double maxY = number(bounds, "maxY", 0.0);
        double maxZ = number(bounds, "maxZ", Double.NaN);
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
            return null;
        }
        JsonElement minElement = bounds.get("min");
        JsonElement maxElement = bounds.get("max");
        if (minElement != null && minElement.isJsonObject()) {
            JsonObject minObject = minElement.getAsJsonObject();
            minX = number(minObject, "x", minX);
            minY = number(minObject, "y", minY);
            minZ = number(minObject, "z", minZ);
        }
        if (maxElement != null && maxElement.isJsonObject()) {
            JsonObject maxObject = maxElement.getAsJsonObject();
            maxX = number(maxObject, "x", maxX);
            maxY = number(maxObject, "y", maxY);
            maxZ = number(maxObject, "z", maxZ);
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static Vec3d[] parsePoints(JsonObject object) {
        JsonElement pointsElement = object.has("points") ? object.get("points")
                : object.has("vertices") ? object.get("vertices") : object.get("polygon");
        if (pointsElement == null || !pointsElement.isJsonArray()) {
            return null;
        }
        JsonArray array = pointsElement.getAsJsonArray();
        Vec3d[] points = new Vec3d[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonObject()) {
                JsonObject point = element.getAsJsonObject();
                points[i] = new Vec3d(number(point, "x", 0.0), number(point, "y", 0.0), number(point, "z", 0.0));
            } else if (element.isJsonArray()) {
                double[] values = numbers(element.getAsJsonArray());
                if (values.length >= 2) {
                    points[i] = new Vec3d(values[0], values.length > 2 ? values[1] : 0.0, values[1]);
                }
            }
        }
        return points;
    }

    private static double[] numbers(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element != null && element.isJsonPrimitive()) {
                values[i] = element.getAsDouble();
            }
        }
        return values;
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static int integer(JsonObject object, String field, int fallback) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static double number(JsonObject object, String field, double fallback) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " não pode ficar vazio");
        }
        return value;
    }
}
