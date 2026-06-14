package dev.EfraGroup.wolfmod.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.EfraGroup.wolfmod.client.ghost.GhostManagerClient;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class GhostToggleCommand {
    private GhostToggleCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ghosttoggle")
                .executes(context -> {
                    boolean enabled = GhostManagerClient.toggleEnabled();
                    context.getSource().sendFeedback(Text.literal(enabled
                            ? "[WolfMod] Ghost ativado."
                            : "[WolfMod] Ghost desativado."));
                    return 1;
                }));
    }
}
