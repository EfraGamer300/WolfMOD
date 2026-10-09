package dev.EfraGroup.wolfmod.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.EfraGroup.wolfmod.client.WolfClient;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfCommands;
import dev.EfraGroup.wolfmod.platform.WolfModList;
import dev.EfraGroup.wolfmod.platform.WolfNetwork;
import dev.EfraGroup.wolfmod.platform.WolfNetworkBridge;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Entry point client do Fabric. Este arquivo substitui o antigo
 * dev.EfraGroup.wolfmod.client.WolfmodClient: a logica foi para o :common
 * (WolfClient) e aqui sobra apenas o wiring das APIs do Fabric.
 */
public class WolfmodFabricClient implements net.fabricmc.api.ClientModInitializer {
    private static KeyBinding radioKeyBinding;
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        WolfClient.bootstrap();
        installBridges();
        registerKeyBindings();
        registerPayloads();
        registerConnectionEvents();
        registerTick();
        registerCommands();
        registerRendering();
    }

    private void installBridges() {
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

        WolfModList.install(() -> FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId() + ":" + m.getMetadata().getVersion().getFriendlyString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private void registerKeyBindings() {
        radioKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wolfnmod.radio",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                "category.wolfnmod.radio"
        ));

        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wolfnmod.radio_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_DECIMAL,
                "category.wolfnmod.radio"
        ));
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostDataPayload.ID, GhostDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RadioPayload.ID, RadioPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(WolfConfigPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        WolfClient.handleWolfConfig(payload, responder(context.responseSender()::sendPacket))));

        ClientPlayNetworking.registerGlobalReceiver(GhostDataPayload.ID, (payload, context) ->
                context.client().execute(() -> WolfClient.handleGhostData(payload)));
    }

    private static WolfResponder responder(java.util.function.Consumer<WolfConfigPayload> sender) {
        return sender::accept;
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                WolfClient.onJoin(WolfResponder.of(WolfNetwork.bridge())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WolfClient.onDisconnect());
    }

    private void registerTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (radioKeyBinding.wasPressed()) {
                WolfClient.startRadioRecording();
            }
            if (configKeyBinding.wasPressed()) {
                client.setScreen(new dev.EfraGroup.wolfmod.client.radio.RadioConfigScreen());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> WolfClient.endTick());
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
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
                        context -> source(context)
                ));
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

    private void registerRendering() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                WolfClient.renderHud(drawContext, tickCounter.getTickDelta(true)));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                WolfClient.renderFastestLap(drawContext));

        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                WolfClient.renderGhost(context.matrixStack(), context.consumers(), context.camera(), context.tickDelta()));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
                ScreenEvents.afterRender(screen).register((screenInstance, context, mouseX, mouseY, tickDelta) ->
                        WolfClient.renderPauseHud(context, tickDelta));
            }
        });
    }
}
