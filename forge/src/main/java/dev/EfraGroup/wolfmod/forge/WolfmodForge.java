package dev.EfraGroup.wolfmod.forge;

import dev.EfraGroup.wolfmod.Wolfmod;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.GhostUploadPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraftforge.network.registration.PayloadRegistrar;

/**
 * Entry point do Forge. Igual ao NeoForge: o architectury-loom remapeia o Forge
 * para YARN, entao o codigo deste modulo compartilha os mesmos nomes do :common.
 */
@Mod(Wolfmod.MOD_ID)
public class WolfmodForge {

    public WolfmodForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Wolfmod.onInitialize();
        modBus.addListener(WolfmodForge::registerPayloads);
        if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            WolfmodForgeClient.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Guarda por dist: este registro tambem roda em servidor dedicado, e um
        // method reference direto para a classe client resolveria o invokestatic
        // aqui, carregando MinecraftClient e estourando NoClassDefFoundError.
        registrar.playToClient(WolfConfigPayload.ID, WolfConfigPayload.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                WolfmodForgeClient.onWolfConfig(payload);
            }
        });
        registrar.playToClient(GhostDataPayload.ID, GhostDataPayload.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                WolfmodForgeClient.onGhostData(payload);
            }
        });

        registrar.playToServer(WolfConfigPayload.ID, WolfConfigPayload.CODEC, (payload, context) -> {
        });
        registrar.playToServer(RadioPayload.ID, RadioPayload.CODEC, (payload, context) -> {
        });
        registrar.playToServer(GhostUploadPayload.ID, GhostUploadPayload.CODEC, (payload, context) -> {
        });
    }
}
