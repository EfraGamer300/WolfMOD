package dev.EfraGroup.wolfmod.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.EfraGroup.wolfmod.client.hud.FastestLap;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FastestLapCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fltest")
                .then(argument("player", StringArgumentType.string())
                        .executes(context -> {
                            // Pega o nome do argumento
                            String playerName = StringArgumentType.getString(context, "player");

                            // Dispara a GUI com o nome personalizado
                            FastestLap.show(playerName, "1:36.323");

                            context.getSource().sendFeedback(Text.literal("§d[WolfMod] §fTestando Fastest Lap para: §b" + playerName));
                            return 1;
                        })
                )
                .executes(context -> {
                    // Se não passar argumentos, usa o padrão (EfraMLG)
                    FastestLap.show();

                    context.getSource().sendFeedback(Text.literal("§d[WolfMod] §fTestando animação de Fastest Lap padrão."));
                    return 1;
                })
        );
    }
}