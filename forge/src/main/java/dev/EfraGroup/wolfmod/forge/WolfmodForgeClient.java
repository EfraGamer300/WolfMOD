package dev.EfraGroup.wolfmod.forge;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.command.CommandSourceStack;
import net.minecraft.server.command.Commands;
import net.minecraft.text.Text;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Wiring client do Forge. Espelha o WolfmodNeoForgeClient evento por evento.
 *
 * Diferencas em relacao ao NeoForge:
 *  - HUD usa RegisterGuiOverlaysEvent (Forge) em vez de RegisterGuiLayersEvent;
 *  - render de mundo usa RenderLevelStageEvent em vez de RenderFrameEvent;
 *  - o mod bus vem de FMLJavaModLoadingContext.
 */
public final class WolfmodForgeClient {
    private static KeyBinding radioKeyBinding;
    private static KeyBinding configKeyBinding;

    private WolfmodForgeClient() {
    }

    /** Chamado so no client - o guard por dist fica no call site (WolfmodForge). */
    public static void init(IEventBus modBus) {
        WolfClient.bootstrap();
        installBridges();

        modBus.addListener(WolfmodForgeClient::registerKeyMappings);
        modBus.addListener(WolfmodForgeClient::registerGuiOverlays);

        MinecraftForge.EVENT_BUS.register(WolfmodForgeClient.class);
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
                "category.wolfnmod.radio");
        configKeyBinding = new KeyBinding(
                "key.wolfnmod.radio_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_DECIMAL,
                "category.wolfnmod.radio");
        event.register(radioKeyBinding);
        event.register(configKeyBinding);
    }

    private static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaForgeGui.HOTBAR, "wolfmod_hud", (guiGraphics, deltaTracker) ->
                WolfClient.renderHud(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAbove(VanillaForgeGui.HOTBAR, "wolfmod_fastest_lap", (guiGraphics, deltaTracker) ->
                WolfClient.renderFastestLap(guiGraphics));
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (radioKeyBinding != null && radioKeyBinding.consumeClick()) {
            WolfClient.startRadioRecording();
        }
        if (configKeyBinding != null && configKeyBinding.consumeClick()) {
            MinecraftClient.getInstance().setScreen(new RadioConfigScreen());
        }
        WolfClient.endTick();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        WolfClient.onJoin(WolfResponder.of(WolfNetwork.bridge()));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WolfClient.onDisconnect();
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterClientCommandsEvent event) {
        WolfClient.registerCommands(
                event.getDispatcher(),
                new WolfCommands.Builder<CommandSourceStack>() {
                    @Override
                    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
                        return Commands.literal(name);
                    }

                    @Override
                    public com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
                        return Commands.argument(name, StringArgumentType.word());
                    }
                },
                WolfmodForgeClient::source);
    }

    private static WolfCommands.Source<CommandSourceStack> source(CommandContext<CommandSourceStack> context) {
        CommandSourceStack src = context.getSource();
        return new WolfCommands.Source<>() {
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

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof GameMenuScreen) {
            WolfClient.renderPauseHud(event.getGuiGraphics(),
                    event.getRenderTickCount().getGameTimeDeltaPartialTick(false));
        }
    }

    static void onWolfConfig(WolfConfigPayload payload) {
        MinecraftClient.getInstance().execute(() ->
                WolfClient.handleWolfConfig(payload, WolfResponder.of(WolfNetwork.bridge())));
    }

    static void onGhostData(GhostDataPayload payload) {
        MinecraftClient.getInstance().execute(() -> WolfClient.handleGhostData(payload));
    }

    /** Referencia aos ids de overlay do vanilla, para o registerAbove. */
    private static final class VanillaForgeGui {
        static final net.minecraft.resources.ResourceLocation HOTBAR =
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/hotbar");

        private VanillaForgeGui() {
        }
    }
}
