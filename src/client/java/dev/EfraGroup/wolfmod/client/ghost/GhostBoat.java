package dev.EfraGroup.wolfmod.client.ghost;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class GhostBoat extends BoatEntity {
    private static final float PADDLE_SPEED = 0.3926991f;

    public GhostBoat(World world) {
        super(EntityType.OAK_BOAT, world, Items.OAK_BOAT::asItem);
    }

    @Override
    public boolean isPaddleMoving(int paddle) {
        return hasPassengers();
    }

    @Override
    public float lerpPaddlePhase(int paddle, float tickProgress) {
        if (!hasPassengers()) {
            return 0.0f;
        }

        float basePhase = (getWorld().getTime() + tickProgress) * PADDLE_SPEED;
        return paddle == 0 ? basePhase : basePhase + 0.8f;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        return ActionResult.PASS;
    }

    public void syncPassenger(Entity passenger) {
        EntityDimensions dimensions = passenger.getDimensions(passenger.getPose());
        Vec3d attachment = getPassengerAttachmentPos(passenger, dimensions, 1.0f);
        double passengerX = getX() + attachment.x;
        double passengerY = getY() + attachment.y;
        double passengerZ = getZ() + attachment.z;

        passenger.setPosition(passengerX, passengerY, passengerZ);
        passenger.prevX = passengerX;
        passenger.prevY = passengerY;
        passenger.prevZ = passengerZ;
        passenger.lastRenderX = passengerX;
        passenger.lastRenderY = passengerY;
        passenger.lastRenderZ = passengerZ;
        passenger.setBodyYaw(getYaw());
        passenger.setHeadYaw(getYaw());
        passenger.setYaw(MathHelper.wrapDegrees(getYaw()));
        passenger.prevYaw = passenger.getYaw();
        passenger.prevPitch = passenger.getPitch();
    }
}
