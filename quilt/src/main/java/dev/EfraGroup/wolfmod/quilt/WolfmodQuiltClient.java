package dev.EfraGroup.wolfmod.quilt;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.EfraGroup.wolfmod.client.WolfClient;
import dev.EfraGroup.wolfmod.client.radio.RadioConfigScreen;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfCommands;
import dev.EfraGroup.wolfmod.platform.WolfModList;
import dev.EfraGroup.wolfmod.platform.WolfNetwork;
import dev.EfraGroup.wolfmod.platform.WolfNetworkBridge;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import java.util.UUID;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.metadata.QuiltMetadata;
import org.quiltmc.qsl.command.api.CommandRegistrationCallback;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientTickEvents;
import org.quiltmc.qsl.networking.api.client.ClientPlayConnectionEvents;
import org.quiltmc.qsl.networking.api.client.ClientPlayNetworkingEvents;
import org.quiltmc.qsl.screen.api.ScreenEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Wiring client do Quilt.
 *
 * O Quilt Loader e um fork do Fabric Loader e o QFAPI reimplementa o Fabric API com
 * os mesmos nomes de classe, entao tick/conexao/pacotes/comandos/tela usam as
 * classes do Fabric API direto.
 *
 * Os dois pontos que o QFAPI nao cobre sao HUD e render de mundo: no Fabric eles
 * vem de HudRenderCallback e WorldRenderEvents. Aqui eles sao cobertos por mixin
 * (DrawContextMixin e WorldRendererMixin), que chamam o mesmo WolfClient.
 */
public final class WolfmodQuiltClient {
    private static KeyBinding radioKeyBinding;
    private static KeyBinding configKeyBinding;

    private WolfmodQuiltClient() {
    }

    public static void init() {
        WolfClient.bootstrap();
        installBridges();

        radioKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wolfnmod.radio", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, "category.wolfnmod.radio"));
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wolfnmod.radio_config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_DECIMAL, "category.wolfnmod.radio"));

        registerPayloads();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                WolfClient.onJoin(WolfResponder.of(WolfNetwork.bridge())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WolfClient.onDisconnect());

        ClientTickEvents.END.register(client -> {
            if (radioKeyBinding.wasPressed()) {
                WolfClient.startRadioRecording();
            }
            if (configKeyBinding.wasPressed()) {
                client.setScreen(new RadioConfigScreen());
            }
            WolfClient.endTick();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
                WolfClient.registerCommands(
                        dispatcher,
                        new WolfCommands.Builder<FabricClientCommandSource>() {
                            @Override
                            public com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
                                return ClientCommandManager.literal(name);
                            }

                            @Override
                            public com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> word(String name) {
                                return ClientCommandManager.argument(name, StringArgumentType.word());
                            }
                        },
                        WolfmodQuiltClient::source));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                ScreenEvents.afterRender(screen).register((screenInstance, context, mouseX, mouseY, tickDelta) ->
                        WolfClient.renderPauseHud(context, tickDelta));
            }
        });
    }

    private static void installBridges() {
        WolfNetwork.install(new WolfNetworkBridge() {
            @Override
            public boolean canSendConfig() {
                return ClientPlayNetworking.canSend(WolfConfigPayload.ID);
            }

            @Override
            public void sendConfig(WolfConfigPayload payload) {
                ClientPlayNetworking.send(payload);
            }

            @Override
            public boolean canSendRadio() {
                return ClientPlayNetworking.canSend(RadioPayload.ID);
            }

            @Override
            public void sendRadio(RadioPayload payload) {
                ClientPlayNetworking.send(payload);
            }
        });

        WolfModList.install(() -> QuiltLoader.getAllMods().stream()
                .map(container -> {
                    QuiltMetadata meta = container.metadata();
                    return meta.id() + ":" + meta.version().raw();
                })
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostDataPayload.ID, GhostDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RadioPayload.ID, RadioPayload.CODEC);

        ClientPlayNetworkingEvents.registerGlobalReceiver(WolfConfigPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        WolfClient.handleWolfConfig(payload, p -> context.packetSender().sendPacket(p))));
        ClientPlayNetworkingEvents.registerGlobalReceiver(GhostDataPayload.ID,
                (payload, context) -> context.client().execute(() -> WolfClient.handleGhostData(payload)));
    }

    private static WolfCommands.Source<FabricClientCommandSource> source(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource src = context.getSource();
        return new WolfCommands.Source<>() {
            @Override
            public UUID playerUuid() {
                return src.getPlayer().getUuid();
            }

            @Override
            public void feedback(Text text) {
                src.sendFeedback(text);
            }
        };
    }
}
