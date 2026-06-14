package dev.EfraGroup.wolfmod.client.commands; // Pacote do Comando

import com.mojang.brigadier.CommandDispatcher;
// O IMPORT QUE RESOLVE O ERRO:
import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class DebugCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("wolfmod")
                .then(ClientCommandManager.literal("start")
                        .executes(context -> {
                            Timer.start(); // Agora ele encontra o Timer!
                            context.getSource().sendFeedback(Text.literal("§6[WolfMod] §aTimer iniciado localmente."));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            Timer.stop();
                            context.getSource().sendFeedback(Text.literal("§6[WolfMod] §cTimer parado localmente."));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("config")
                        .executes(context -> {
                            Timer.stop();
                            context.getSource().sendFeedback(Text.literal("§6[WolfMod] §cTimer parado localmente."));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("reset")
                        .executes(context -> {
                            Timer.reset();
                            context.getSource().sendFeedback(Text.literal("§6[WolfMod] §eTimer resetado."));
                            return 1;
                        }))
        );
    }
}