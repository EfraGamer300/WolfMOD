package dev.EfraGroup.wolfmod.client.ghost;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class GhostPlayer extends OtherClientPlayerEntity {
    public GhostPlayer(ClientWorld world) {
        super(world, createProfile());
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        return ActionResult.PASS;
    }

    @SuppressWarnings("unchecked")
    private static GameProfile createProfile() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return new GameProfile(UUID.randomUUID(), "Ghost");
        }

        GameProfile currentProfile = client.player.getGameProfile();
        GameProfile profile = new GameProfile(UUID.randomUUID(), currentProfile.getName() + " Ghost");
        profile.getProperties().putAll((Multimap<String, Property>) currentProfile.getProperties());
        return profile;
    }
}
