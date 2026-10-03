package dev.EfraGroup.wolfmod.client.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class CarPhysics {

    private static final double[] GEAR_TOP = {0.0, 0.28, 0.45, 0.65, 0.90, 1.20, 1.50};
    private static final double[] GEAR_ACCEL = {0.0, 0.032, 0.026, 0.021, 0.017, 0.014, 0.012};
    private static final double FINAL_TOP = 1.50;
    private static final double BOOST_MULT = 1.15;
    private static final double MAX_REVERSE = 0.30;
    private static final double REVERSE_ACCEL = 0.010;

    private static final double UPSHIFT_AT = 0.92;
    private static final double DOWNSHIFT_AT = 0.45;
    private static final int SHIFT_COOLDOWN = 6;

    private static final double BRAKE_FORCE = 0.030;
    private static final double HANDBRAKE_FORCE = 0.050;
    private static final double COAST_DECEL = 0.0020;
    private static final double ROLLING_RESIST = 0.0003;
    private static final double DRAG_LINEAR = 0.0003;
    private static final double DRAG_QUAD = 0.0016;

    private static final double GRIP_BASE = 0.92;
    private static final double GRIP_LOW_SPEED = 0.99;
    private static final double DRIFT_GRIP = 0.22;
    private static final double HANDBRAKE_GRIP = 0.10;
    private static final double LATERAL_SLIP_CAP = 0.55;

    private static final double STEER_LOCK_DEG = 32.0;
    private static final double STEER_SPEED_DEG = 140.0;
    private static final double RETURN_SPEED_DEG = 220.0;
    private static final double ACKERMANN_GRIP_BONUS = 0.12;

    private static final double DOWNFORCE = 0.35;
    private static final double WEIGHT_TRANSFER = 0.30;
    private static final double STEP_LIFT = 0.42;
    private static final double MAX_FALL = 0.70;

    private static final double REDLINE_RPM = 7500.0;
    private static final double IDLE_RPM = 900.0;
    private static final double UPSHIFT_RPM = 6800.0;
    private static final double DOWNSHIFT_RPM = 2600.0;
    private static final double REV_MATCH_BLIP = 1200.0;

    private static final double KMH_FACTOR = 72.0;

    private static final Map<UUID, CarState> STATES = new HashMap<>();
    private static final Map<UUID, Boolean> ENABLED = new HashMap<>();
    private static final Map<UUID, Boolean> AUTO_GEAR = new HashMap<>();
    private static final Map<UUID, Integer> SHIFT_QUEUE = new HashMap<>();

    private CarPhysics() {
    }

    public static boolean isEnabled(UUID playerId) {
        return ENABLED.getOrDefault(playerId, false);
    }

    public static boolean toggle(UUID playerId) {
        boolean on = !isEnabled(playerId);
        setEnabled(playerId, on);
        return on;
    }

    public static void setEnabled(UUID playerId, boolean on) {
        if (on) {
            ENABLED.put(playerId, true);
            AUTO_GEAR.putIfAbsent(playerId, true);
        } else {
            ENABLED.remove(playerId);
            STATES.remove(playerId);
            SHIFT_QUEUE.remove(playerId);
        }
    }

    public static boolean isAuto(UUID playerId) {
        return AUTO_GEAR.getOrDefault(playerId, true);
    }

    public static void setAuto(UUID playerId, boolean auto) {
        AUTO_GEAR.put(playerId, auto);
    }

    public static boolean toggleAuto(UUID playerId) {
        boolean auto = !isAuto(playerId);
        setAuto(playerId, auto);
        return auto;
    }

    public static void clear() {
        ENABLED.clear();
        STATES.clear();
        AUTO_GEAR.clear();
        SHIFT_QUEUE.clear();
    }

    public static void queueShift(UUID playerId, int direction) {
        if (!isEnabled(playerId) || isAuto(playerId)) {
            return;
        }
        SHIFT_QUEUE.put(playerId, direction);
    }

    public static CarSnapshot snapshot(UUID playerId) {
        CarState state = STATES.get(playerId);
        if (state == null) {
            return new CarSnapshot(0, 0.0, 0.0, 0.0, false, true);
        }
        return new CarSnapshot(state.gear, state.rpm, state.speed * KMH_FACTOR,
                state.steerAngleDeg, state.drifting, isAuto(playerId));
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        UUID playerId = client.player.getUuid();
        if (!isEnabled(playerId)) {
            return;
        }
        if (!(client.player.getVehicle() instanceof AbstractBoatEntity boat)) {
            STATES.remove(playerId);
            return;
        }
        drive(client, boat);
    }

    public static boolean drive(MinecraftClient client, AbstractBoatEntity boat) {
        UUID playerId = client.player.getUuid();
        CarState state = STATES.computeIfAbsent(playerId, key -> new CarState());

        GameOptions options = client.options;
        boolean forward = options.forwardKey.isPressed();
        boolean backward = options.backKey.isPressed();
        boolean left = options.leftKey.isPressed();
        boolean right = options.rightKey.isPressed();
        boolean handbrake = options.sneakKey.isPressed();
        boolean driftKey = options.jumpKey.isPressed();
        boolean boosting = options.sprintKey.isPressed();

        double dt = 1.0 / 20.0;

        updateSteering(state, left, right, dt);

        if (state.shiftCooldown > 0) {
            state.shiftCooldown--;
        }

        boolean auto = isAuto(playerId);
        Integer queued = SHIFT_QUEUE.remove(playerId);
        if (auto) {
            autoShift(state);
        } else {
            manualShift(state, queued);
        }

        double top = FINAL_TOP * (boosting ? BOOST_MULT : 1.0);
        double speed = state.speed;

        if (handbrake) {
            speed = approach(speed, 0.0, HANDBRAKE_FORCE);
        } else if (forward && !backward) {
            if (speed < -0.02) {
                speed = approach(speed, 0.0, BRAKE_FORCE);
            } else {
                speed += engineForce(state, speed);
                speed -= dragForce(speed);
                speed = approach(speed, 0.0, ROLLING_RESIST);
            }
        } else if (backward && !forward) {
            if (speed > 0.03) {
                speed = approach(speed, 0.0, BRAKE_FORCE);
            } else {
                speed -= REVERSE_ACCEL * (1.0 - Math.min(1.0, -speed / MAX_REVERSE));
                speed -= dragForce(speed);
            }
        } else {
            speed = approach(speed, 0.0, COAST_DECEL);
            speed -= speed * (DRAG_LINEAR + DRAG_QUAD * Math.abs(speed));
            if (Math.abs(speed) < 0.003) {
                speed = 0.0;
            }
        }

        if (!forward && speed > 0.0) {
            speed -= speed * speed * DOWNFORCE * dt * 0.15;
        }

        speed = clamp(speed, -MAX_REVERSE, top);

        updateRpm(state, speed, forward, backward);

        float yaw = normalizeYaw(boat.getYaw());
        double speedAbs = Math.abs(speed);
        if (speedAbs > 0.008 && Math.abs(state.steerAngleDeg) > 0.05) {
            double wheelBase = 2.2;
            double steerRad = Math.toRadians(state.steerAngleDeg);
            double speedFactor = Math.min(1.0, speedAbs / 0.12);
            double highCut = 1.0 / (1.0 + (speedAbs / FINAL_TOP) * (speedAbs / FINAL_TOP) * 6.0);
            double yawRate = Math.toDegrees(Math.atan(Math.tan(steerRad) / wheelBase) * speedAbs * 3.2);
            yawRate *= speedFactor * highCut;
            if (handbrake || driftKey) {
                yawRate *= 1.9;
            }
            yawRate = clamp(yawRate, -14.0, 14.0);
            yaw = normalizeYaw(yaw + (float) (yawRate * (speed < 0.0 ? -1.0 : 1.0)));
        }

        Vec3d heading = yawToDirection(yaw);

        double grip = currentGrip(state, speedAbs, handbrake, driftKey, boosting);
        double transfer = 1.0 + WEIGHT_TRANSFER * clamp(state.longAccel, -1.0, 1.0) * 0.5;
        grip = clamp(grip * transfer, 0.05, 1.0);

        Vec3d current = new Vec3d(boat.getVelocity().x, 0.0, boat.getVelocity().z);
        Vec3d want = heading.multiply(speed);

        Vec3d lateralVel = current.subtract(heading.multiply(current.dotProduct(heading)));
        double lateralMag = lateralVel.length();
        boolean drifting = lateralMag > 0.09 && speedAbs > 0.15;
        state.drifting = drifting || ((handbrake || driftKey) && speedAbs > 0.25 && Math.abs(state.steerAngleDeg) > 4.0);

        if (lateralMag > LATERAL_SLIP_CAP) {
            lateralVel = lateralVel.normalize().multiply(LATERAL_SLIP_CAP);
            current = heading.multiply(current.dotProduct(heading)).add(lateralVel);
        }

        double ackermann = 1.0 + ACKERMANN_GRIP_BONUS * (1.0 - Math.min(1.0, Math.abs(state.steerAngleDeg) / STEER_LOCK_DEG));
        grip = clamp(grip * ackermann, 0.05, 1.0);

        Vec3d horizontal = current.multiply(1.0 - grip).add(want.multiply(grip));

        double newLongAccel = (horizontal.subtract(current).dotProduct(heading)) * 20.0;
        state.longAccel = state.longAccel * 0.8 + newLongAccel * 0.2;

        double vy = resolveVertical(boat, horizontal);
        horizontal = new Vec3d(horizontal.x, vy, horizontal.z);

        boat.setYaw(yaw);
        boat.setVelocity(horizontal);
        boat.velocityDirty = true;
        state.speed = speed;

        sendHud(client, playerId, state, boosting);

        return true;
    }

    private static void updateSteering(CarState state, boolean left, boolean right, double dt) {
        double input = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double speedAbs = Math.abs(state.speed);
        double lockScale = 1.0 / (1.0 + (speedAbs / FINAL_TOP) * 2.4);
        double maxAngle = STEER_LOCK_DEG * lockScale;
        if (input != 0.0) {
            double target = input * maxAngle;
            double step = STEER_SPEED_DEG * lockScale * dt;
            state.steerAngleDeg = approach(state.steerAngleDeg, target, step);
        } else {
            state.steerAngleDeg = approach(state.steerAngleDeg, 0.0, RETURN_SPEED_DEG * dt);
        }
        state.steerAngleDeg = clamp(state.steerAngleDeg, -STEER_LOCK_DEG, STEER_LOCK_DEG);
    }

    private static void autoShift(CarState state) {
        if (state.speed < 0.01) {
            if (state.gear > 1) {
                state.gear = 1;
            }
            return;
        }
        if (state.shiftCooldown > 0) {
            return;
        }
        int gear = clampInt(state.gear, 1, 6);
        if (state.speed >= GEAR_TOP[gear] * UPSHIFT_AT && gear < 6) {
            state.gear = gear + 1;
            state.shiftCooldown = SHIFT_COOLDOWN;
            state.rpm = Math.max(state.rpm - 2800.0, IDLE_RPM + 400.0);
        } else if (state.rpm >= UPSHIFT_RPM && gear < 6) {
            state.gear = gear + 1;
            state.shiftCooldown = SHIFT_COOLDOWN;
            state.rpm = Math.max(state.rpm - 2800.0, IDLE_RPM + 400.0);
        } else if (gear > 1 && (state.speed < GEAR_TOP[gear - 1] * DOWNSHIFT_AT
                || (state.rpm <= DOWNSHIFT_RPM && state.speed > 0.02))) {
            state.gear = gear - 1;
            state.shiftCooldown = SHIFT_COOLDOWN;
            state.rpm = Math.min(state.rpm + 1800.0 + REV_MATCH_BLIP * 0.4, REDLINE_RPM - 400.0);
        }
        state.gear = clampInt(state.gear, 1, 6);
    }

    private static void manualShift(CarState state, Integer queued) {
        if (queued != null && state.shiftCooldown <= 0) {
            if (queued > 0 && state.gear < 6) {
                state.gear++;
                state.shiftCooldown = SHIFT_COOLDOWN + 2;
                state.rpm = Math.max(state.rpm - 2600.0, IDLE_RPM);
            } else if (queued < 0 && state.gear > 1) {
                state.gear--;
                state.shiftCooldown = SHIFT_COOLDOWN + 2;
                state.rpm = Math.min(state.rpm + 1500.0, REDLINE_RPM - 300.0);
            }
        }
        if (state.rpm >= REDLINE_RPM + 400.0) {
            state.rpm = REDLINE_RPM + 400.0;
        }
    }

    private static double engineForce(CarState state, double speed) {
        int gear = clampInt(state.gear, 1, 6);
        double gearTop = GEAR_TOP[gear];
        double accel = GEAR_ACCEL[gear];
        if (speed >= gearTop) {
            if (gear < 6) {
                return accel * 0.60;
            }
            return 0.0;
        }
        double ratio = clamp(speed / Math.max(gearTop, 0.001), 0.0, 1.0);
        double curve = 1.0 - 0.65 * ratio * ratio;
        double rpmFactor = 0.55 + 0.45 * clamp(state.rpm / REDLINE_RPM, 0.0, 1.0);
        return accel * Math.max(0.35, curve) * (0.6 + 0.4 * rpmFactor);
    }

    private static double dragForce(double speed) {
        return DRAG_LINEAR * speed + DRAG_QUAD * speed * Math.abs(speed);
    }

    private static void updateRpm(CarState state, double speed, boolean forward, boolean backward) {
        int gear = clampInt(state.gear, 1, 6);
        double gearTop = Math.max(GEAR_TOP[gear], 0.001);
        double ratio = clamp(Math.abs(speed) / gearTop, 0.0, 1.12);
        double target = IDLE_RPM + ratio * (REDLINE_RPM - IDLE_RPM);
        if (!forward && !backward && Math.abs(speed) < 0.01) {
            target = IDLE_RPM;
        }
        if (state.rpm < target) {
            state.rpm = Math.min(target, state.rpm + 400.0);
        } else {
            state.rpm = Math.max(target, state.rpm - 300.0);
        }
        state.rpm = clamp(state.rpm, IDLE_RPM * 0.8, REDLINE_RPM + 400.0);
    }

    private static double currentGrip(CarState state, double speedAbs, boolean handbrake, boolean driftKey, boolean boosting) {
        if (handbrake) {
            return HANDBRAKE_GRIP;
        }
        if (driftKey) {
            return DRIFT_GRIP;
        }
        double speedRatio = Math.min(1.0, speedAbs / FINAL_TOP);
        double grip = GRIP_LOW_SPEED + (GRIP_BASE - GRIP_LOW_SPEED) * speedRatio;
        grip += DOWNFORCE * speedRatio * 0.08;
        if (boosting) {
            grip -= 0.04;
        }
        if (state.drifting) {
            grip = Math.min(grip, 0.45);
        }
        return clamp(grip, 0.05, 1.0);
    }

    private static void sendHud(MinecraftClient client, UUID playerId, CarState state, boolean boosting) {
        if (client.player == null) {
            return;
        }
        double kmh = Math.abs(state.speed) * KMH_FACTOR;
        String gearLabel = state.speed < -0.01 ? "R" : String.valueOf(clampInt(state.gear, 1, 6));
        if (Math.abs(state.speed) < 0.005) {
            gearLabel = "N";
        }
        boolean redline = state.rpm >= UPSHIFT_RPM;
        String mode = isAuto(playerId) ? "AUTO" : "MAN";
        String msg = String.format("§e[Carro] §f%3.0f km/h §7| §b%s §7%s §8%s §7%s%s%s",
                kmh, gearLabel, bar(state.rpm), mode,
                redline ? "§c● " : "", state.drifting ? "§6DRIFT " : "", boosting ? "§dNITRO" : "");
        client.player.sendMessage(Text.literal(msg), true);
    }

    private static String bar(double rpm) {
        int total = 10;
        double fill = clamp((rpm - IDLE_RPM) / (REDLINE_RPM - IDLE_RPM), 0.0, 1.0);
        int on = (int) Math.round(fill * total);
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < total; i++) {
            if (i < on) {
                sb.append(i >= 8 ? "§c|" : (i >= 6 ? "§e|" : "§a|"));
            } else {
                sb.append("§8|");
            }
        }
        sb.append("§8]");
        return sb.toString();
    }

    private static double resolveVertical(AbstractBoatEntity boat, Vec3d horizontal) {
        double rawVy = boat.getVelocity().getY();
        if (boat.isOnGround()) {
            double speedAbs = Math.sqrt(horizontal.x * horizontal.x + horizontal.z * horizontal.z);
            return stepLift(boat, horizontal, speedAbs);
        }
        return Math.max(rawVy - 0.08, -MAX_FALL);
    }

    private static double stepLift(AbstractBoatEntity boat, Vec3d horizontal, double speedAbs) {
        if (speedAbs < 0.20) {
            return 0.0;
        }
        Vec3d pos = boat.getPos();
        Vec3d ahead = pos.add(horizontal.normalize().multiply(1.0));
        if (!boat.getWorld().getBlockState(new net.minecraft.util.math.BlockPos(
                (int) Math.floor(ahead.x), (int) Math.floor(pos.y), (int) Math.floor(ahead.z)))
                .isAir()
                && boat.getWorld().getBlockState(new net.minecraft.util.math.BlockPos(
                (int) Math.floor(ahead.x), (int) Math.floor(pos.y) + 1, (int) Math.floor(ahead.z)))
                .isAir()) {
            return STEP_LIFT;
        }
        return 0.0;
    }

    private static Vec3d yawToDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized >= 180.0f) {
            normalized -= 360.0f;
        }
        if (normalized < -180.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    private static double approach(double value, double target, double delta) {
        if (value < target) {
            return Math.min(target, value + delta);
        }
        if (value > target) {
            return Math.max(target, value - delta);
        }
        return target;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record CarSnapshot(int gear, double rpm, double kmh, double steerDeg, boolean drifting, boolean auto) {
    }

    private static final class CarState {
        private double speed;
        private double rpm = IDLE_RPM;
        private int gear = 1;
        private double steerAngleDeg;
        private double longAccel;
        private boolean drifting;
        private int shiftCooldown;
    }
}
