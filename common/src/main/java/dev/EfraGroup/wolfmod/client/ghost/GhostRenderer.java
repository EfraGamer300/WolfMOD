package dev.EfraGroup.wolfmod.client.ghost;

import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;

public final class GhostRenderer {
    private static final float GHOST_ALPHA = 0.50f;

    private static ClientWorld cachedWorld;
    private static GhostBoat ghostBoat;
    private static GhostPlayer ghostRider;

    private GhostRenderer() {}

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        GhostPose pose = GhostManagerClient.currentPose();

        if (pose == null || client.world == null || client.player == null || consumers == null || matrices == null || camera == null) {
            return;
        }

        ensureEntities(client);
        if (ghostBoat == null || ghostRider == null) {
            return;
        }

        syncEntity(ghostBoat, pose.x(), pose.y(), pose.z(), pose.yaw(), 0.0f);
        syncEntity(ghostRider, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        mountRider();
        ghostBoat.setPaddlesMoving(true, true);
        ghostBoat.syncPassenger(ghostRider);
        ghostRider.setPose(EntityPose.SITTING);

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Vec3d cameraPos = camera.getPos();
        VertexConsumerProvider translucentConsumers = new AlphaVertexConsumerProvider(consumers, GHOST_ALPHA);

        dispatcher.configure(client.world, camera, client.player);
        dispatcher.setRenderShadows(false);
        renderGhostEntity(ghostBoat, matrices, dispatcher, cameraPos, translucentConsumers);
        renderGhostEntity(ghostRider, matrices, dispatcher, cameraPos, translucentConsumers);
        dispatcher.setRenderShadows(true);
    }

    private static void renderGhostEntity(Entity entity, MatrixStack matrices, EntityRenderDispatcher dispatcher, Vec3d cameraPos, VertexConsumerProvider consumers) {
        double x = entity.getX() - cameraPos.x;
        double y = entity.getY() - cameraPos.y;
        double z = entity.getZ() - cameraPos.z;
        dispatcher.render(entity, x, y, z, entity.getYaw(), matrices, consumers, 15728880);
    }

    private static void ensureEntities(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            cachedWorld = null;
            ghostBoat = null;
            ghostRider = null;
            return;
        }

        if (client.world == cachedWorld && ghostBoat != null && ghostRider != null) {
            return;
        }

        cachedWorld = client.world;
        ghostBoat = new GhostBoat(client.world);
        ghostBoat.setNoGravity(true);
        ghostBoat.noClip = true;
        ghostBoat.setSilent(true);

        ghostRider = new GhostPlayer(client.world);
        ghostRider.setNoGravity(true);
        ghostRider.noClip = true;
        ghostRider.setSilent(true);
    }

    private static void syncEntity(Entity entity, double x, double y, double z, float yaw, float pitch) {
        entity.setPosition(x, y, z);
        entity.prevX = x;
        entity.prevY = y;
        entity.prevZ = z;
        entity.lastRenderX = x;
        entity.lastRenderY = y;
        entity.lastRenderZ = z;

        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.prevYaw = yaw;
        entity.prevPitch = pitch;

        if (entity instanceof GhostPlayer rider) {
            rider.headYaw = yaw;
            rider.prevHeadYaw = yaw;
            rider.bodyYaw = yaw;
            rider.prevBodyYaw = yaw;
        }
    }

    private static void mountRider() {
        if (ghostBoat == null || ghostRider == null) {
            return;
        }

        if (ghostRider.getVehicle() != ghostBoat) {
            ghostRider.startRiding(ghostBoat, true);
        }
    }

    private record AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alphaScale) implements VertexConsumerProvider {
        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new AlphaVertexConsumer(delegate.getBuffer(layer), alphaScale);
        }
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alphaScale) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, Math.round(alpha * alphaScale));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }
}
