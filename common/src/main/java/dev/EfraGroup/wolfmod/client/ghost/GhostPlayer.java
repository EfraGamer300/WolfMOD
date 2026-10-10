package dev.EfraGroup.wolfmod.client.ghost;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
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

    /**
     * Espelha o BoatLabs: esconde a capa do ghost (field_7559 = CAPE,
     * primeiro valor do enum) e usa a skin do jogador local.
     */
    @Override
    public boolean isPartVisible(PlayerModelPart modelPart) {
        return modelPart != PlayerModelPart.CAPE;
    }

    @Override
    public SkinTextures getSkinTextures() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getSkinTextures();
        }
        return super.getSkinTextures();
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
