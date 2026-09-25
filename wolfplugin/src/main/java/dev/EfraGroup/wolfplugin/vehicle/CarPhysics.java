package dev.EfraGroup.wolfplugin.vehicle;

import dev.EfraGroup.wolfplugin.WolfPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CarPhysics {

    private static final double TOP_SPEED = 0.85;
    private static final double BOOST_MULT = 1.30;
    private static final double MAX_REVERSE = 0.30;
    private static final double ACCEL = 0.045;
    private static final double ACCEL_REVERSE = 0.030;
    private static final double BRAKE_FORCE = 0.10;
    private static final double HANDBRAKE_FORCE = 0.16;
    private static final double COAST_DECEL = 0.012;
    private static final double DRAG = 0.008;
    private static final double GRIP = 0.90;
    private static final double DRIFT_GRIP = 0.30;
    private static final double STEER_DEG = 4.2;
    private static final double GRAVITY = 0.08;
    private static final double MAX_FALL = 0.70;
    private static final double STEP_LIFT = 0.42;

    private static final Input NEUTRAL = new Input() {
        @Override public boolean isForward() { return false; }
        @Override public boolean isBackward() { return false; }
        @Override public boolean isLeft() { return false; }
        @Override public boolean isRight() { return false; }
        @Override public boolean isJump() { return false; }
        @Override public boolean isSneak() { return false; }
        @Override public boolean isSprint() { return false; }
    };

    private final WolfPlugin plugin;
    private final Set<UUID> enabled = new HashSet<>();
    private final Map<UUID, CarState> states = new HashMap<>();
    private final Map<UUID, UUID> lastBoat = new HashMap<>();
    private BukkitTask tickTask;

    public CarPhysics(WolfPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Map.Entry<UUID, UUID> entry : lastBoat.entrySet()) {
            Entity entity = plugin.getServer().getEntity(entry.getValue());
            if (entity instanceof Boat boat) {
                restore(boat);
            }
        }
        states.clear();
        lastBoat.clear();
        enabled.clear();
    }

    public boolean isEnabled(UUID playerId) {
        return enabled.contains(playerId);
    }

    public void setEnabled(UUID playerId, boolean on) {
        if (on) {
            enabled.add(playerId);
        } else {
            enabled.remove(playerId);
            restorePlayerBoat(playerId);
        }
    }

    public boolean toggle(UUID playerId) {
        boolean on = !enabled.contains(playerId);
        setEnabled(playerId, on);
        return on;
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Entity vehicle = player.getVehicle();
            if (enabled.contains(playerId)
                    && vehicle instanceof Boat boat
                    && boat.getPassengers().contains(player)) {
                lastBoat.put(playerId, boat.getUniqueId());
                Input input = player.getCurrentInput();
                drive(boat, input != null ? input : NEUTRAL);
            } else {
                restoreIfLeft(playerId);
            }
        }
        pruneUnusedStates();
    }

    private void restoreIfLeft(UUID playerId) {
        UUID boatId = lastBoat.remove(playerId);
        if (boatId == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(boatId);
        if (entity instanceof Boat boat) {
            restore(boat);
        }
    }

    private void restorePlayerBoat(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.getVehicle() instanceof Boat boat) {
            restore(boat);
        }
        restoreIfLeft(playerId);
    }

    private void drive(Boat boat, Input input) {
        CarState state = states.computeIfAbsent(boat.getUniqueId(), key -> new CarState());
        if (!state.captured) {
            capture(boat, state);
        }

        boat.setWorkOnLand(true);
        neutralize(boat);

        boolean forward = input.isForward();
        boolean backward = input.isBackward();
        boolean left = input.isLeft();
        boolean right = input.isRight();
        boolean handbrake = input.isSneak();
        boolean drifting = input.isJump();
        boolean boosting = input.isSprint();

        double top = TOP_SPEED * (boosting ? BOOST_MULT : 1.0);
        double speed = state.speed;

        if (handbrake) {
            speed = approach(speed, 0.0, HANDBRAKE_FORCE);
        } else if (forward && !backward) {
            if (speed < -0.02) {
                speed = approach(speed, 0.0, BRAKE_FORCE);
            } else {
                double curve = 1.0 - Math.min(speed / top, 1.0);
                speed += ACCEL * Math.max(0.25, curve);
            }
        } else if (backward && !forward) {
            if (speed > 0.03) {
                speed = approach(speed, 0.0, BRAKE_FORCE);
            } else {
                double curve = 1.0 - Math.min(-speed / MAX_REVERSE, 1.0);
                speed -= ACCEL_REVERSE * Math.max(0.30, curve);
            }
        } else {
            speed = approach(speed, 0.0, COAST_DECEL);
        }

        speed -= speed * DRAG;
        if (Math.abs(speed) < 0.004 && !forward && !backward) {
            speed = 0.0;
        }
        speed = clamp(speed, -MAX_REVERSE, top);

        float yaw = normalizeYaw(boat.getLocation().getYaw());
        double steer = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double speedAbs = Math.abs(speed);
        if (steer != 0.0 && speedAbs > 0.01) {
            double agility = Math.min(1.0, speedAbs / 0.20);
            double highSpeedCut = 1.0 - 0.5 * Math.min(1.0, speedAbs / top);
            double rate = STEER_DEG * agility * highSpeedCut * (drifting ? 1.4 : 1.0);
            yaw = normalizeYaw(yaw + (float) (steer * rate * (speed < 0.0 ? -1.0 : 1.0)));
        }

        Vector heading = yawToDirection(yaw);
        Vector want = heading.clone().multiply(speed);

        Vector current = boat.getVelocity().clone();
        current.setY(0.0);

        double grip = drifting ? DRIFT_GRIP : GRIP;
        Vector horizontal = current.multiply(1.0 - grip).add(want.multiply(grip));

        double rawVy = boat.getVelocity().getY();
        double vy = resolveVertical(boat, heading, speedAbs, rawVy);

        boat.setRotation(yaw, 0.0f);
        horizontal.setY(vy);
        boat.setVelocity(horizontal);

        state.speed = speed;

        if (forward && !handbrake && speed > 0.15) {
            spawnExhaust(boat, heading, boat.getWorld());
        }
    }

    private double resolveVertical(Boat boat, Vector heading, double speedAbs, double rawVy) {
        if (boat.isOnGround()) {
            return stepLift(boat, heading, speedAbs);
        }
        Boat.Status status = boat.getStatus();
        if (status == Boat.Status.IN_WATER
                || status == Boat.Status.UNDER_WATER
                || status == Boat.Status.UNDER_FLOWING_WATER) {
            return clamp(rawVy * 0.85 + 0.015, -0.12, 0.10);
        }
        return Math.max(rawVy - GRAVITY, -MAX_FALL);
    }

    private double stepLift(Boat boat, Vector heading, double speedAbs) {
        if (speedAbs < 0.20) {
            return 0.0;
        }
        Location probe = boat.getLocation().clone().add(heading.clone().multiply(1.0));
        Block front = probe.getBlock();
        Block head1 = probe.clone().add(0.0, 1.0, 0.0).getBlock();
        Block head2 = probe.clone().add(0.0, 2.0, 0.0).getBlock();
        if (!front.isPassable() && head1.isPassable() && head2.isPassable()) {
            return STEP_LIFT;
        }
        return 0.0;
    }

    @SuppressWarnings("deprecation")
    private void capture(Boat boat, CarState state) {
        state.maxSpeed = boat.getMaxSpeed();
        state.occupiedDeceleration = boat.getOccupiedDeceleration();
        state.unoccupiedDeceleration = boat.getUnoccupiedDeceleration();
        state.workOnLand = boat.getWorkOnLand();
        state.captured = true;
    }

    @SuppressWarnings("deprecation")
    private void neutralize(Boat boat) {
        boat.setMaxSpeed(0.0D);
        boat.setOccupiedDeceleration(0.0D);
        boat.setUnoccupiedDeceleration(0.0D);
    }

    @SuppressWarnings("deprecation")
    private void restore(Boat boat) {
        CarState state = states.remove(boat.getUniqueId());
        if (state == null || !state.captured) {
            return;
        }
        boat.setMaxSpeed(state.maxSpeed);
        boat.setOccupiedDeceleration(state.occupiedDeceleration);
        boat.setUnoccupiedDeceleration(state.unoccupiedDeceleration);
        boat.setWorkOnLand(state.workOnLand);
    }

    private void pruneUnusedStates() {
        states.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null);
    }

    private void spawnExhaust(Boat boat, Vector forward, World world) {
        Location base = boat.getLocation().clone().add(0.0, 0.35, 0.0);
        Vector backward = forward.clone().multiply(-0.85);
        Vector side = new Vector(-forward.getZ(), 0.0, forward.getX()).multiply(0.22);

        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(backward).add(side), 1, 0.02, 0.02, 0.02, 0.0);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(backward).subtract(side), 1, 0.02, 0.02, 0.02, 0.0);
    }

    private static Vector yawToDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        return new Vector(x, 0.0, z).normalize();
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

    private static final class CarState {
        private double speed;
        private boolean captured;
        private double maxSpeed;
        private double occupiedDeceleration;
        private double unoccupiedDeceleration;
        private boolean workOnLand;
    }
}
