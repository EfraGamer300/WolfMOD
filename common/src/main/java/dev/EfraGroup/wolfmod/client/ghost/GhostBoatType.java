package dev.EfraGroup.wolfmod.client.ghost;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;

/**
 * Selectable boat hull for the personal-best ghost, mirroring the BoatLabs mod.
 *
 * <p>Each constant carries the matching {@link EntityType} and the item supplier
 * the boat entity needs for its drops/stack. {@link #fromId} is tolerant of
 * unknown or blank ids so a stale config never breaks ghost rendering.
 */
public enum GhostBoatType {
    OAK("oak", EntityType.OAK_BOAT, Items.OAK_BOAT),
    BIRCH("birch", EntityType.BIRCH_BOAT, Items.BIRCH_BOAT),
    SPRUCE("spruce", EntityType.SPRUCE_BOAT, Items.SPRUCE_BOAT),
    JUNGLE("jungle", EntityType.JUNGLE_BOAT, Items.JUNGLE_BOAT),
    ACACIA("acacia", EntityType.ACACIA_BOAT, Items.ACACIA_BOAT),
    DARK_OAK("dark_oak", EntityType.DARK_OAK_BOAT, Items.DARK_OAK_BOAT),
    MANGROVE("mangrove", EntityType.MANGROVE_BOAT, Items.MANGROVE_BOAT),
    CHERRY("cherry", EntityType.CHERRY_BOAT, Items.CHERRY_BOAT),
    PALE_OAK("pale_oak", EntityType.PALE_OAK_BOAT, Items.PALE_OAK_BOAT);

    public final String id;
    public final EntityType<? extends net.minecraft.entity.vehicle.BoatEntity> entityType;
    public final net.minecraft.item.Item item;

    GhostBoatType(String id, EntityType<? extends net.minecraft.entity.vehicle.BoatEntity> entityType, net.minecraft.item.Item item) {
        this.id = id;
        this.entityType = entityType;
        this.item = item;
    }

    /** Resolves an id (case-insensitive) to a type, falling back to OAK. */
    public static GhostBoatType fromId(String id) {
        if (id == null || id.isBlank()) {
            return OAK;
        }
        String normalized = id.trim().toLowerCase().replace(' ', '_');
        for (GhostBoatType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return OAK;
    }
}
