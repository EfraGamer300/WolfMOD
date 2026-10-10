package dev.EfraGroup.wolfmod.client.ghost;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the personal best trajectory as a line on the ground.
 *
 * <p>Complements {@link GhostRenderer}: the boat and rider show where the lap was at this
 * instant, this shows the whole path taken. Rendered with {@link RenderLayer#getLines()} so it
 * reads as a thin trail rather than a solid tube.
 *
 * <p>Drawn in world space relative to the camera, and skipped when the lap would fall behind
 * the live run so the finished portion does not clutter the track.
 */
public final class GhostLineRenderer {

    /** Offset above the recorded boat position, so the line is not buried in the road. */
    private static final double HEIGHT_OFFSET = 0.06;

    /**
     * Distance from the camera beyond which points are skipped. Keeps vertex count sane on
     * long laps and stops the trail of the previous run sitting across half the world.
     */
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;

    private GhostLineRenderer() {
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || matrices == null
                || consumers == null || camera == null) {
            return;
        }
        if (!GhostManagerClient.isLineVisible()) {
            return;
        }

        GhostLine line = GhostManagerClient.currentLine();
        if (line == null || line.pointCount() < 2) {
            return;
        }

        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Depth test off: the trail is a guide that has to stay readable even when the boat
        // drove through ice, water or terrain. It is drawn on top rather than occluded,
        // which matches how the server-side heart particles already read.
        RenderSystem.disableDepthTest();

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        Vec3d previous = null;
        for (int i = 0; i < line.pointCount(); i++) {
            Vec3d point = line.pointAt(i);
            if (point == null) {
                continue;
            }
            if (previous != null && previous.squaredDistanceTo(point) > MAX_RENDER_DISTANCE_SQUARED) {
                // Break the strip instead of bridging across the gap.
                previous = null;
                continue;
            }
            if (previous != null) {
                consumer.vertex(matrices.peek().getPositionMatrix(),
                                (float) previous.x, (float) previous.y, (float) previous.z)
                        .color(line.color())
                        .normal((float) (cameraPos.x - previous.x),
                                (float) (cameraPos.y - previous.y),
                                (float) (cameraPos.z - previous.z));
                consumer.vertex(matrices.peek().getPositionMatrix(),
                                (float) point.x, (float) point.y, (float) point.z)
                        .color(line.color())
                        .normal((float) (cameraPos.x - point.x),
                                (float) (cameraPos.y - point.y),
                                (float) (cameraPos.z - point.z));
            }
            previous = point;
        }
        matrices.pop();

        // Restore whatever the rest of the frame expects.
        RenderSystem.enableDepthTest();
    }

    /**
     * Position-only view of a recorded lap, used for drawing the trail.
     *
     * <p>Deliberately decoupled from {@link GhostData} so the renderer does not depend on
     * timing arrays it never reads.
     */
    public record GhostLine(Vec3d[] points, int color) {

        public int pointCount() {
            return points == null ? 0 : points.length;
        }

        public Vec3d pointAt(int index) {
            return points == null || index < 0 || index >= points.length ? null : points[index];
        }

        /** Builds a line from a ghost payload's position arrays, applying {@code heightOffset}. */
        public static GhostLine from(float[] xs, float[] ys, float[] zs, int color, double heightOffset) {
            if (xs == null || ys == null || zs == null) {
                return null;
            }
            int count = Math.min(xs.length, Math.min(ys.length, zs.length));
            if (count < 2) {
                return null;
            }
            Vec3d[] points = new Vec3d[count];
            for (int i = 0; i < count; i++) {
                points[i] = new Vec3d(xs[i], ys[i] + heightOffset, zs[i]);
            }
            return new GhostLine(points, color);
        }
    }
}