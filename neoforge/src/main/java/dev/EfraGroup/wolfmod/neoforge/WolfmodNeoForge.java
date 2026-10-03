package dev.EfraGroup.wolfmod.neoforge;

import dev.EfraGroup.wolfmod.Wolfmod;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Entry point do NeoForge. O codigo deste modulo tambem esta em mappings YARN,
 * porque o architectury-loom remapeia o NeoForge para o mesmo namespace do
 * :common - por isso WolfConfigPayload.ID (que em Mojmap e CustomPacketPayload.Type)
 * pode ser usado direto aqui.
 */
@Mod(Wolfmod.MOD_ID)
public class WolfmodNeoForge {

    public WolfmodNeoForge(IEventBus modBus, ModContainer container) {
        Wolfmod.onInitialize();
        modBus.addListener(WolfmodNeoForge::registerPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            WolfmodNeoForgeClient.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Este registro tambem roda em servidor dedicado. Os handlers nao podem
        // tocar em WolfmodNeoForgeClient direto: o method reference resolveria o
        // invokestatic aqui, carregando a classe client e estourando
        // NoClassDefFoundError de MinecraftClient no servidor. O guard por dist
        // mantem a referencia simbolica sem chegar a ser resolvida.
        registrar.playToClient(WolfConfigPayload.ID, WolfConfigPayload.CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        WolfmodNeoForgeClient.onWolfConfig(payload);
                    }
                });
        registrar.playToClient(GhostDataPayload.ID, GhostDataPayload.CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        WolfmodNeoForgeClient.onGhostData(payload);
                    }
                });

        // O cliente so envia; quem responde e o WolfPlugin (Paper) do outro lado.
        registrar.playToServer(WolfConfigPayload.ID, WolfConfigPayload.CODEC, (payload, context) -> {
        });
        registrar.playToServer(RadioPayload.ID, RadioPayload.CODEC, (payload, context) -> {
        });
    }
}
