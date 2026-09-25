package dev.EfraGroup.wolfmod.client;

import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import dev.EfraGroup.wolfmod.client.TimeTrial.WolfTimingClient;
import dev.EfraGroup.wolfmod.client.commands.CarPhysicsCommand;
import dev.EfraGroup.wolfmod.client.commands.DebugCommand;
import dev.EfraGroup.wolfmod.client.commands.FastestLapCommand;
import dev.EfraGroup.wolfmod.client.commands.GhostToggleCommand;
import dev.EfraGroup.wolfmod.client.ghost.GhostManagerClient;
import dev.EfraGroup.wolfmod.client.ghost.GhostRenderer;
import dev.EfraGroup.wolfmod.client.hud.FastestLap;
import dev.EfraGroup.wolfmod.client.hud.Hud;
import dev.EfraGroup.wolfmod.client.hud.PauseHud;
import dev.EfraGroup.wolfmod.client.radio.RadioConfigScreen;
import dev.EfraGroup.wolfmod.client.radio.RadioManager;
import dev.EfraGroup.wolfmod.client.radio.RadioSettings;
import dev.EfraGroup.wolfmod.client.vehicle.CarPhysics;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class WolfmodClient implements ClientModInitializer {
    public static final double MOD_VERSION = 0.1;
    private static KeyBinding radioKeyBinding;
    private static KeyBinding configKeyBinding;
    private static WolfTimingClient timingClient;

    @Override
    public void onInitializeClient() {
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

        RadioSettings.load();
        timingClient = new WolfTimingClient();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                timingClient.onJoin(client, sender));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (radioKeyBinding.wasPressed()) {
                RadioManager.startRecording();
            }
            if (configKeyBinding.wasPressed()) {
                client.setScreen(new RadioConfigScreen());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            DebugCommand.register(dispatcher);
            FastestLapCommand.register(dispatcher);
            GhostToggleCommand.register(dispatcher);
            CarPhysicsCommand.register(dispatcher);
        });

        PayloadTypeRegistry.playS2C().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostDataPayload.ID, GhostDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WolfConfigPayload.ID, WolfConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RadioPayload.ID, RadioPayload.CODEC);

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            Hud.render(drawContext, tickCounter.getTickDelta(true));
        });

        HudRenderCallback.EVENT.register(new FastestLap());
        WorldRenderEvents.AFTER_ENTITIES.register(GhostRenderer::render);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                ScreenEvents.afterRender(screen).register((screenInstance, context, mouseX, mouseY, tickDelta) -> {
                    PauseHud.render(context, tickDelta);
                });
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerInfoManager.reset();
            GhostManagerClient.clearGhost();
            if (timingClient != null) {
                timingClient.onDisconnect();
            }
            Timer.reset();
            CarPhysics.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CarPhysics.tick(client);
            if (timingClient != null) {
                timingClient.tick(client);
            }
            if (client.world != null && !client.isPaused()) {
                Timer.update();
                FastestLap.tick();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(WolfConfigPayload.ID, (payload, context) -> {
            String key = payload.key();
            if (WolfTimingClient.isTimingKey(key)) {
                context.client().execute(() -> {
                    if (timingClient != null) {
                        timingClient.handle(payload, context.responseSender());
                    }
                });
                return;
            }
            switch (key) {
                case "1" -> {
                    String mcVersion = MinecraftClient.getInstance().getGameVersion();
                    String data = "ModV:" + MOD_VERSION + "|MC:" + mcVersion;
                    context.responseSender().sendPacket(new WolfConfigPayload("version_reply", data));
                }
                case "2" -> context.client().execute(() -> {
                    if (timingClient == null || !timingClient.ownsTimer()) {
                        Timer.start();
                    }
                    GhostManagerClient.startPlayback();
                });
                case "3" -> context.client().execute(() -> {
                    if (timingClient == null || !timingClient.ownsTimer()) {
                        Timer.stop();
                    }
                    GhostManagerClient.stopPlayback();
                });
                case "4" -> context.client().execute(() -> FastestLap.show(payload.value(), "1:36.530"));
                case "ghost_start" -> context.client().execute(GhostManagerClient::startPlayback);
                case "ghost_stop" -> context.client().execute(GhostManagerClient::stopPlayback);
                case "ghost_clear" -> context.client().execute(GhostManagerClient::clearGhost);
                case "tire_ack" -> context.client().execute(() -> {
                    if (context.player() != null) {
                        context.player().sendMessage(Text.literal("§e[Rádio] §aPneu selecionado para o próximo pit: §e" + payload.value()), true);
                    }
                });
                case "ers" -> context.client().execute(() -> context.player().sendMessage(Text.literal("Â§6[Wolf] Â§fERS: Â§e" + payload.value()), true));
                case "server_info" -> context.client().execute(() -> ServerInfoManager.updateFromServer(payload.value()));
                case "wolfac_checkmods" -> {
                    String requestId = payload.value();
                    context.client().execute(() -> {
                        String mods;
                        try {
                            mods = net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
                                    .map(m -> m.getMetadata().getId() + ":"
                                            + m.getMetadata().getVersion().getFriendlyString())
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining(","));
                            if (mods.length() > 30000) mods = mods.substring(0, 30000);
                        } catch (Exception e) {
                            mods = "error:" + e.getMessage();
                        }
                        context.responseSender().sendPacket(new WolfConfigPayload("wolfac_mods", requestId + "|" + mods));
                    });
                }
                default -> {
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(GhostDataPayload.ID, (payload, context) ->
                context.client().execute(() -> GhostManagerClient.loadGhost(payload))
        );
    }
}
