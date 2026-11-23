package dev.EfraGroup.wolfmod.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.vehicle.BoatEntity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Mirrors the BoatLabs DisplayPassengerFix: keeps Display entities (text displays)
 * attached to boats in sync with the boat's yaw every client tick.
 */
public final class DisplayPassengerFix {
    private static final Set<Integer> trackedDisplays = new HashSet<>();

    private DisplayPassengerFix() {
    }

    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity) {
                trackedDisplays.add(entity.getId());
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity) {
                trackedDisplays.remove(entity.getId());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(DisplayPassengerFix::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.world == null || trackedDisplays.isEmpty()) {
            return;
        }
        Iterator<Integer> iterator = trackedDisplays.iterator();
        while (iterator.hasNext()) {
            int id = iterator.next();
            Entity entity = client.world.getEntityById(id);
            if (!(entity instanceof DisplayEntity display)) {
                iterator.remove();
                continue;
            }
            Entity vehicle = display.getVehicle();
            if (vehicle == null) {
                continue;
            }
            if (!(vehicle instanceof BoatEntity boat)) {
                continue;
            }
            float yaw = boat.getYaw();
            display.setYaw(yaw);
            display.prevYaw = yaw;
        }
    }
}
