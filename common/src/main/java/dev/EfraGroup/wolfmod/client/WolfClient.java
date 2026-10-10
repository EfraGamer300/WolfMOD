package dev.EfraGroup.wolfmod.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import dev.EfraGroup.wolfmod.client.TimeTrial.WolfTimingClient;
import dev.EfraGroup.wolfmod.client.ghost.GhostManagerClient;
import dev.EfraGroup.wolfmod.client.hud.FastestLap;
import dev.EfraGroup.wolfmod.client.pack.PackSync;
import dev.EfraGroup.wolfmod.client.hud.Hud;
import dev.EfraGroup.wolfmod.client.hud.PauseHud;
import dev.EfraGroup.wolfmod.client.radio.RadioManager;
import dev.EfraGroup.wolfmod.client.radio.RadioSettings;
import dev.EfraGroup.wolfmod.client.vehicle.CarPhysics;
import dev.EfraGroup.wolfmod.network.GhostDataPayload;
import dev.EfraGroup.wolfmod.network.WolfConfigPayload;
import dev.EfraGroup.wolfmod.platform.WolfCommands;
import dev.EfraGroup.wolfmod.platform.WolfModList;
import dev.EfraGroup.wolfmod.platform.WolfResponder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Logica client-side compartilhada. Este e o unico lugar onde o jogo e orquestrado;
 * cada modulo de loader so chama estes metodos a partir dos eventos que ele possui
 * (tick, HUD, render do mundo, tela de pausa, comandos, pacotes e conexao).
 */
public final class WolfClient {
    public static final double MOD_VERSION = 0.1;

    private static WolfTimingClient timingClient = new WolfTimingClient();

    private WolfClient() {
    }

    /** Chamado uma vez no boot do client. */
    public static void bootstrap() {
        RadioSettings.load();
        PackSync.checkForUpdatesAsync();
    }

    public static WolfTimingClient timing() {
        return timingClient;
    }

    public static <S> void registerCommands(
            CommandDispatcher<S> dispatcher,
            WolfCommands.Builder<S> builder,
            Function<CommandContext<S>, WolfCommands.Source<S>> source
    ) {
        WolfCommands.registerAll(dispatcher, builder, source);
    }

    public static void onJoin(WolfResponder responder) {
        ServerInfoManager.checkCurrentServer();
        if (timingClient != null) {
            timingClient.onJoin(MinecraftClient.getInstance(), responder);
        }
    }

    public static void onDisconnect() {
        ServerInfoManager.reset();
        GhostManagerClient.clearGhost();
        if (timingClient != null) {
            timingClient.onDisconnect();
        }
        Timer.reset();
        CarPhysics.clear();
    }

    /** Fim de tick do client. */
    public static void endTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        PackSync.tickLaunchApply(client);
        CarPhysics.tick(client);
        if (timingClient != null) {
            timingClient.tick(client);
        }
        if (client.world != null && !client.isPaused()) {
            Timer.update();
            FastestLap.tick();
            GhostManagerClient.tickGhost(client);
        }
    }

    /** Camada de HUD principal. */
    public static void renderHud(DrawContext context, float tickDelta) {
        Hud.render(context, tickDelta);
    }

    /** Animacao de fastest lap. */
    public static void renderFastestLap(DrawContext context) {
        FastestLap.render(context);
    }

    /** HUD extra da tela de pausa (ESC). */
    public static void renderPauseHud(DrawContext context, float tickDelta) {
        PauseHud.render(context, tickDelta);
    }

    public static void startRadioRecording() {
        RadioManager.startRecording();
    }

    public static void handleWolfConfig(WolfConfigPayload payload, WolfResponder responder) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
        String key = payload.key();
        if (WolfTimingClient.isTimingKey(key)) {
            if (timingClient != null) {
                timingClient.handle(payload, responder);
            }
            return;
        }
        switch (key) {
            case "1" -> {
                String mcVersion = MinecraftClient.getInstance().getGameVersion();
                String data = "ModV:" + MOD_VERSION + "|MC:" + mcVersion;
                if (responder != null) {
                    responder.sendConfig(new WolfConfigPayload("version_reply", data));
                }
            }
            case "2" -> {
                if (timingClient == null || !timingClient.ownsTimer()) {
                    Timer.start();
                }
                GhostManagerClient.startPlayback();
            }
            case "3" -> {
                if (timingClient == null || !timingClient.ownsTimer()) {
                    Timer.stop();
                }
                GhostManagerClient.stopPlayback();
            }
            case "4" -> FastestLap.show(payload.value(), "1:36.530");
            case "ghost_start" -> GhostManagerClient.startPlayback();
            case "ghost_stop" -> GhostManagerClient.stopPlayback();
            case "ghost_clear" -> GhostManagerClient.clearGhost();
            case "tire_ack" -> actionbar(payload, "§e[Rádio] §aPneu selecionado para o próximo pit: §e");
            case "ers" -> actionbar(payload, "Â§6[Wolf] Â§fERS: Â§e");
            case "server_info" -> ServerInfoManager.updateFromServer(payload.value());
            case "pack_manifest" ->
                PackSync.onPackManifest(MinecraftClient.getInstance(), responder, payload.value());
            case "wolfac_checkmods" -> {
                String requestId = payload.value();
                if (responder != null) {
                    responder.sendConfig(new WolfConfigPayload(
                            "wolfac_mods", requestId + "|" + WolfModList.describeTruncated(30000)));
                }
            }
            default -> {
            }
        }
    }

    public static void handleGhostData(GhostDataPayload payload) {
        if (!ServerInfoManager.isOnWolfNetwork()) return;
        GhostManagerClient.loadGhost(payload);
    }

    private static void actionbar(WolfConfigPayload payload, String prefix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(prefix + payload.value()), true);
        }
    }
}
