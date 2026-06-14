package dev.EfraGroup.wolfplugin.vehicle;

import dev.EfraGroup.wolfplugin.WolfPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CarPhysics {
    // Simulated vehicle mass. Higher values make throttle and braking feel heavier.
    private static final double MASS = 1520.0;
    // Forward engine force. Raise for faster launches.
    private static final double ENGINE_FORCE = 6400.0;
    // Reverse engine force. Keep lower than forward force for believable reverse.
    private static final double REVERSE_ENGINE_FORCE = 2900.0;
    // Brake force applied before the boat starts reversing.
    private static final double BRAKING_FORCE = 8600.0;
    // Aerodynamic drag and rolling resistance used while coasting.
    private static final double DRAG_COEFFICIENT = 0.020;
    private static final double ROLLING_RESISTANCE = 0.060;
    // Top speed limits, in blocks per tick.
    private static final double TOP_SPEED = 1.00;
    private static final double MAX_REVERSE_SPEED = 0.28;
    // High lateral grip keeps the boat glued to its forward axis.
    private static final double TRACTION = 0.96;
    // Input filtering. Lower values feel softer and less twitchy.
    private static final double THROTTLE_RESPONSE = 0.10;
    private static final double THROTTLE_RETURN = 0.08;
    // Lateral slip damping keeps transitions smooth instead of twitchy.
    private static final double LATERAL_SMOOTHING = 0.35;
    // Stronger low-speed damping prevents endless crawling.
    private static final double LOW_SPEED_DAMPING = 0.92;
    // If the client stops sending inputs, discard them after this many ticks.
    private static final long INPUT_TIMEOUT_TICKS = 12L;
    private static final double TICK_SECONDS = 1.0 / 20.0;

    private final WolfPlugin plugin;
    private final Map<UUID, Double> currentSpeeds = new HashMap<>();
    private final Map<UUID, Double> throttleStates = new HashMap<>();
    private final Map<UUID, Double> lateralVelocities = new HashMap<>();
    private final Map<UUID, Float> lockedYaws = new HashMap<>();
    private final Map<UUID, InputState> playerInputs = new HashMap<>();
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

        currentSpeeds.clear();
        throttleStates.clear();
        lateralVelocities.clear();
        lockedYaws.clear();
        playerInputs.clear();
    }

    public void updateInput(Player player, boolean forward, boolean backward, boolean left, boolean right) {
        playerInputs.put(player.getUniqueId(), new InputState(forward, backward, left, right, plugin.getServer().getCurrentTick()));
    }

    public void clearInput(UUID playerId) {
        playerInputs.remove(playerId);
    }

    private void tick() {
        expireStaleInputs();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Entity vehicle = player.getVehicle();
            if (!(vehicle instanceof Boat boat)) {
                clearInput(player.getUniqueId());
                continue;
            }

            InputState input = playerInputs.get(player.getUniqueId());
            if (input == null) {
                input = InputState.idle(plugin.getServer().getCurrentTick());
            }

            applyCarPhysics(boat, player, input);
        }

        pruneUnusedStates();
    }

    @SuppressWarnings("deprecation")
    private void applyCarPhysics(Boat boat, Player driver, InputState input) {
        UUID boatId = boat.getUniqueId();

        boat.setWorkOnLand(true);
        boat.setMaxSpeed(0.0D);
        boat.setOccupiedDeceleration(0.0D);
        boat.setUnoccupiedDeceleration(0.0D);

        Vector forward = yawToDirection(boat.getLocation().getYaw());
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
        Vector rawVelocity = boat.getVelocity();

        double projectedSpeed = rawVelocity.clone().setY(0.0).dot(forward);
        double currentSpeed = currentSpeeds.getOrDefault(boatId, projectedSpeed);
        double throttleState = throttleStates.getOrDefault(boatId, 0.0);
        double smoothedLateralVelocity = lateralVelocities.getOrDefault(boatId, 0.0);
        float lockedYaw = lockedYaws.getOrDefault(boatId, normalizeYaw(boat.getLocation().getYaw()));

        double throttleInput = input.forward() == input.backward() ? 0.0 : (input.forward() ? 1.0 : -1.0);
        double throttleSmoothing = Math.abs(throttleInput) > 0.01 ? THROTTLE_RESPONSE : THROTTLE_RETURN;
        throttleState += (throttleInput - throttleState) * throttleSmoothing;

        if (Math.abs(throttleState) < 0.01 && Math.abs(throttleInput) < 0.01) {
            throttleState = 0.0;
        }

        currentSpeed = integrateSpeed(currentSpeed, throttleState);

        // Hard-lock the hull yaw so the boat never rotates, even when the rider turns the camera.
        boat.setRotation(lockedYaw, 0.0f);
        forward = yawToDirection(lockedYaw);
        right = new Vector(-forward.getZ(), 0.0, forward.getX());

        double lateralVelocity = rawVelocity.clone().setY(0.0).dot(right);
        smoothedLateralVelocity += ((lateralVelocity * TRACTION) - smoothedLateralVelocity) * LATERAL_SMOOTHING;

        Vector composedVelocity = forward.multiply(currentSpeed).add(right.multiply(smoothedLateralVelocity));
        composedVelocity.setY(resolveVerticalVelocity(boat, rawVelocity));

        if (Math.abs(currentSpeed) < 0.004 && Math.abs(smoothedLateralVelocity) < 0.004 && throttleState == 0.0) {
            composedVelocity.multiply(LOW_SPEED_DAMPING);
            currentSpeed = 0.0;
            smoothedLateralVelocity = 0.0;
        }

        boat.setVelocity(composedVelocity);
        currentSpeeds.put(boatId, currentSpeed);
        throttleStates.put(boatId, throttleState);
        lateralVelocities.put(boatId, smoothedLateralVelocity);
        lockedYaws.put(boatId, lockedYaw);

        if (throttleState > 0.20 && currentSpeed > 0.08) {
            spawnExhaust(boat, forward, driver.getWorld());
        }
    }

    private double integrateSpeed(double currentSpeed, double throttleInput) {
        if (throttleInput > 0.0) {
            if (currentSpeed < -0.02) {
                currentSpeed = Math.min(0.0, currentSpeed + forceToAcceleration(BRAKING_FORCE));
            } else {
                double torqueCurve = 1.0 - Math.min(Math.max(currentSpeed, 0.0) / TOP_SPEED, 1.0);
                currentSpeed += forceToAcceleration(ENGINE_FORCE) * Math.max(0.20, torqueCurve);
            }
        } else if (throttleInput < 0.0) {
            if (currentSpeed > 0.02) {
                currentSpeed = Math.max(0.0, currentSpeed - forceToAcceleration(BRAKING_FORCE));
            } else {
                double reverseCurve = 1.0 - Math.min(Math.abs(currentSpeed) / MAX_REVERSE_SPEED, 1.0);
                currentSpeed -= forceToAcceleration(REVERSE_ENGINE_FORCE) * Math.max(0.26, reverseCurve);
            }
        } else {
            currentSpeed = applyPassiveLoss(currentSpeed);
        }

        currentSpeed = applyPassiveLoss(currentSpeed);
        return clamp(currentSpeed, -MAX_REVERSE_SPEED, TOP_SPEED);
    }

    private double applyPassiveLoss(double speed) {
        if (Math.abs(speed) < 0.0008) {
            return 0.0;
        }

        double deceleration = (DRAG_COEFFICIENT + ROLLING_RESISTANCE) * TICK_SECONDS;
        if (speed > 0.0) {
            return Math.max(0.0, speed - deceleration);
        }
        return Math.min(0.0, speed + deceleration);
    }

    private double resolveVerticalVelocity(Boat boat, Vector rawVelocity) {
        if (boat.getStatus() == Boat.Status.IN_WATER || boat.getStatus() == Boat.Status.UNDER_WATER || boat.getStatus() == Boat.Status.UNDER_FLOWING_WATER) {
            return Math.max(rawVelocity.getY(), -0.08);
        }
        return Math.min(rawVelocity.getY(), 0.0);
    }

    private void spawnExhaust(Boat boat, Vector forward, World world) {
        Location base = boat.getLocation().clone().add(0.0, 0.35, 0.0);
        Vector backward = forward.clone().multiply(-0.85);
        Vector side = new Vector(-forward.getZ(), 0.0, forward.getX()).multiply(0.22);

        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(backward).add(side), 1, 0.02, 0.02, 0.02, 0.0);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(backward).subtract(side), 1, 0.02, 0.02, 0.02, 0.0);
    }

    private void expireStaleInputs() {
        long currentTick = plugin.getServer().getCurrentTick();
        Iterator<Map.Entry<UUID, InputState>> iterator = playerInputs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, InputState> entry = iterator.next();
            if ((currentTick - entry.getValue().serverTick()) > INPUT_TIMEOUT_TICKS) {
                iterator.remove();
            }
        }
    }

    private void pruneUnusedStates() {
        currentSpeeds.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null);
        throttleStates.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null);
        lateralVelocities.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null);
        lockedYaws.entrySet().removeIf(entry -> plugin.getServer().getEntity(entry.getKey()) == null);
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

    private static double forceToAcceleration(double force) {
        return (force / MASS) * TICK_SECONDS;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record InputState(boolean forward, boolean backward, boolean left, boolean right, long serverTick) {
        private static InputState idle(long serverTick) {
            return new InputState(false, false, false, false, serverTick);
        }
    }
}
