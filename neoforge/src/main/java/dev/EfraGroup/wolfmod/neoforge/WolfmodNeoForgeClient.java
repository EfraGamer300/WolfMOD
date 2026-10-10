package dev.EfraGroup.wolfmod.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.EfraGroup.wolfmod.client.WolfClient;
import dev.EfraGroup.wolfmod.client.radio.RadioConfigScreen;
import dev.EfraGroup.wolfmod.network.GhostUploadPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfModList;
import dev.EfraGroup.wolfmod.platform.WolfNetwork;
import dev.EfraGroup.wolfmod.platform.WolfNetworkBridge;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.command.CommandSourceStack;
import net.minecraft.server.command.Commands;
import net.minecraft.text.Text;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Wiring client do NeoForge. Equivalente ao WolfmodFabricClient: cada metodo aqui
 * corresponde a um evento do NeoForge e delega para o WolfClient do :common.
 */
public final class WolfmodNeoForgeClient {
    private static KeyBinding radioKeyBinding;
    private static KeyBinding configKeyBinding;

    private WolfmodNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        WolfClient.bootstrap();
        installBridges();

        modBus.addListener(WolfmodNeoForgeClient::registerKeyMappings);
        modBus.addListener(WolfmodNeoForgeClient::registerGuiLayers);

        NeoForge.EVENT_BUS.addListener(WolfmodNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(WolfmodNeoForgeClient::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(WolfmodNeoForgeClient::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(WolfmodNeoForgeClient::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(WolfmodNeoForgeClient::onScreenRenderPost);
    }

    private static void installBridges() {
        WolfNetwork.install(new WolfNetworkBridge() {
            @Override
            public boolean canSendConfig() {
                return MinecraftClient.getInstance().getNetworkHandler() != null;
            }

            @Override
            public void sendConfig(WolfConfigPayload payload) {
                PacketDistributor.sendToServer(payload);
            }

            @Override
            public boolean canSendRadio() {
                return MinecraftClient.getInstance().getNetworkHandler() != null;
            }

            @Override
            public void sendRadio(RadioPayload payload) {
                PacketDistributor.sendToServer(payload);
            }

            @Override
            public boolean canSendGhostUpload() {
                return MinecraftClient.getInstance().getNetworkHandler() != null;
            }

            @Override
            public void sendGhostUpload(GhostUploadPayload payload) {
                PacketDistributor.sendToServer(payload);
            }
        });

        WolfModList.install(() -> ModList.get().getMods().stream()
                .map(info -> info.getModId() + ":" + info.getVersion().toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        radioKeyBinding = new KeyBinding(
                "key.wolfnmod.radio",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                "category.wolfnmod.radio"
        );
        configKeyBinding = new KeyBinding(
                "key.wolfnmod.radio_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_DECIMAL,
                "category.wolfnmod.radio"
        );
        event.register(radioKeyBinding);
        event.register(configKeyBinding);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, "wolfmod_hud", (guiGraphics, deltaTracker) ->
                WolfClient.renderHud(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAbove(VanillaGuiLayers.HOTBAR, "wolfmod_fastest_lap", (guiGraphics, deltaTracker) ->
                WolfClient.renderFastestLap(guiGraphics));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (radioKeyBinding != null && radioKeyBinding.consumeClick()) {
            WolfClient.startRadioRecording();
        }
        if (configKeyBinding != null && configKeyBinding.consumeClick()) {
            MinecraftClient.getInstance().setScreen(new RadioConfigScreen());
        }
        WolfClient.endTick();
    }

    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        WolfClient.onJoin(WolfResponder.of(WolfNetwork.bridge()));
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WolfClient.onDisconnect();
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        WolfClient.registerCommands(
                event.getDispatcher(),
                new dev.EfraGroup.wolfmod.platform.WolfCommands.Builder<CommandSourceStack>() {
                    @Override
                    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
                        return Commands.literal(name);
                    }

                    @Override
                    public com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
                        return Commands.argument(name, StringArgumentType.word());
                    }
                },
                WolfmodNeoForgeClient::source
        );
    }

    private static dev.EfraGroup.wolfmod.platform.WolfCommands.Source<CommandSourceStack> source(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack src = context.getSource();
        return new dev.EfraGroup.wolfmod.platform.WolfCommands.Source<>() {
            @Override
            public UUID playerUuid() {
                return src.getPlayerOrException().getUuid();
            }

            @Override
            public void feedback(Text text) {
                src.sendSuccess(() -> text, false);
            }
        };
    }

    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof GameMenuScreen) {
            WolfClient.renderPauseHud(event.getGuiGraphics(), event.getRenderTickCount().getGameTimeDeltaPartialTick(false));
        }
    }

    static void onWolfConfig(WolfConfigPayload payload) {
        MinecraftClient.getInstance().execute(() ->
                WolfClient.handleWolfConfig(payload, WolfResponder.of(WolfNetwork.bridge())));
    }

    static void onGhostData(dev.EfraGroup.wolfmod.network.GhostDataPayload payload) {
        MinecraftClient.getInstance().execute(() -> WolfClient.handleGhostData(payload));
    }
}
