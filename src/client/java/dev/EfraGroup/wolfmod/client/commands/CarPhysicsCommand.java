package dev.EfraGroup.wolfmod.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.EfraGroup.wolfmod.client.vehicle.CarPhysics;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class CarPhysicsCommand {

    private CarPhysicsCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("carphysics")
                .then(literal("up").executes(context -> {
                    CarPhysics.queueShift(context.getSource().getPlayer().getUuid(), 1);
                    context.getSource().sendFeedback(Text.literal("§e[Wolf] §fMarcha +"));
                    return 1;
                }))
                .then(literal("down").executes(context -> {
                    CarPhysics.queueShift(context.getSource().getPlayer().getUuid(), -1);
                    context.getSource().sendFeedback(Text.literal("§e[Wolf] §fMarcha -"));
                    return 1;
                }))
                .then(literal("auto").executes(context -> {
                    CarPhysics.setAuto(context.getSource().getPlayer().getUuid(), true);
                    context.getSource().sendFeedback(Text.literal("§e[Wolf] §aCâmbio AUTOMÁTICO."));
                    return 1;
                }))
                .then(literal("manual").executes(context -> {
                    CarPhysics.setAuto(context.getSource().getPlayer().getUuid(), false);
                    context.getSource().sendFeedback(Text.literal("§e[Wolf] §bCâmbio MANUAL §7(R/F ou /carphysics up|down)."));
                    return 1;
                }))
                .then(argument("state", StringArgumentType.word())
                        .executes(context -> {
                            String state = StringArgumentType.getString(context, "state");
                            boolean on = parse(context.getSource(), state);
                            context.getSource().sendFeedback(Text.literal(on
                                    ? "§e[Wolf] §aModo carro ATIVADO."
                                    : "§e[Wolf] §cModo carro DESATIVADO."));
                            return 1;
                        }))
                .executes(context -> {
                    boolean on = CarPhysics.toggle(context.getSource().getPlayer().getUuid());
                    context.getSource().sendFeedback(Text.literal(on
                            ? "§e[Wolf] §aModo carro ATIVADO."
                            : "§e[Wolf] §cModo carro DESATIVADO."));
                    return 1;
                }));
    }

    private static boolean parse(FabricClientCommandSource source, String state) {
        boolean on;
        if ("on".equalsIgnoreCase(state)) {
            on = true;
        } else if ("off".equalsIgnoreCase(state)) {
            on = false;
        } else if ("auto".equalsIgnoreCase(state)) {
            CarPhysics.setAuto(source.getPlayer().getUuid(), true);
            source.sendFeedback(Text.literal("§e[Wolf] §aCâmbio AUTOMÁTICO."));
            return CarPhysics.isEnabled(source.getPlayer().getUuid());
        } else if ("manual".equalsIgnoreCase(state)) {
            CarPhysics.setAuto(source.getPlayer().getUuid(), false);
            source.sendFeedback(Text.literal("§e[Wolf] §bCâmbio MANUAL."));
            return CarPhysics.isEnabled(source.getPlayer().getUuid());
        } else {
            source.sendFeedback(Text.literal("§cUse: /carphysics [on|off|auto|manual|up|down]"));
            return CarPhysics.isEnabled(source.getPlayer().getUuid());
        }
        CarPhysics.setEnabled(source.getPlayer().getUuid(), on);
        return on;
    }
}
