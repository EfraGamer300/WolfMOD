package dev.EfraGroup.wolfmod.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.EfraGroup.wolfmod.client.ServerInfoManager;
import dev.EfraGroup.wolfmod.client.TimeTrial.Timer;
import dev.EfraGroup.wolfmod.client.ghost.GhostManagerClient;
import dev.EfraGroup.wolfmod.client.hud.FastestLap;
import dev.EfraGroup.wolfmod.client.vehicle.CarPhysics;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.text.Text;

/**
 * Constroi a arvore de comandos cliente uma unica vez, no :common, apesar de cada
 * loader usar um tipo de source diferente:
 *
 *   fabric    -> FabricClientCommandSource (ClientCommandManager.literal/argument)
 *   quilt     -> QuiltClientCommandSource  (QuiltClientCommandManager.literal/argument)
 *   neoforge  -> CommandSourceStack         (Commands.literal/argument)
 *   forge     -> CommandSourceStack         (Commands.literal/argument)
 *
 * O modulo do loader so precisa fornecer as duas lambdas de {@link Builder} e
 * {@link Source}.
 */
public final class WolfCommands {
    private WolfCommands() {
    }

    /** Fabrica de nos da arvore, com o tipo de source do loader. */
    public interface Builder<S> {
        LiteralArgumentBuilder<S> literal(String name);

        RequiredArgumentBuilder<S, String> word(String name);
    }

    /** Acesso a source, que cada loader expoe de um jeito proprio. */
    public interface Source<S> {
        UUID playerUuid();

        void feedback(Text text);
    }

    public static <S> void registerAll(
            CommandDispatcher<S> dispatcher,
            Builder<S> builder,
            Function<CommandContext<S>, Source<S>> source
    ) {
        registerDebug(dispatcher, builder, source);
        registerFastestLap(dispatcher, builder, source);
        registerGhostToggle(dispatcher, builder, source);
        registerCarPhysics(dispatcher, builder, source);
    }

    private static <S> void registerDebug(
            CommandDispatcher<S> dispatcher,
            Builder<S> builder,
            Function<CommandContext<S>, Source<S>> source
    ) {
        dispatcher.register(builder.literal("wolfmod")
                .requires(src -> ServerInfoManager.isOnWolfNetwork())
                .then(builder.literal("start").executes(context -> {
                    Timer.start();
                    source.apply(context).feedback(Text.literal("§6[WolfMod] §aTimer iniciado localmente."));
                    return 1;
                }))
                .then(builder.literal("stop").executes(context -> {
                    Timer.stop();
                    source.apply(context).feedback(Text.literal("§6[WolfMod] §cTimer parado localmente."));
                    return 1;
                }))
                .then(builder.literal("config").executes(context -> {
                    Timer.stop();
                    source.apply(context).feedback(Text.literal("§6[WolfMod] §cTimer parado localmente."));
                    return 1;
                }))
                .then(builder.literal("reset").executes(context -> {
                    Timer.reset();
                    source.apply(context).feedback(Text.literal("§6[WolfMod] §eTimer resetado."));
                    return 1;
                })));
    }

    private static <S> void registerFastestLap(
            CommandDispatcher<S> dispatcher,
            Builder<S> builder,
            Function<CommandContext<S>, Source<S>> source
    ) {
        dispatcher.register(builder.literal("fltest")
                .requires(src -> ServerInfoManager.isOnWolfNetwork())
                .then(builder.word("player").executes(context -> {
                    String playerName = StringArgumentType.getString(context, "player");
                    FastestLap.show(playerName, "1:36.323");
                    source.apply(context).feedback(Text.literal("§d[WolfMod] §fTestando Fastest Lap para: §b" + playerName));
                    return 1;
                }))
                .executes(context -> {
                    FastestLap.show();
                    source.apply(context).feedback(Text.literal("§d[WolfMod] §fTestando animação de Fastest Lap padrão."));
                    return 1;
                }));
    }

    private static <S> void registerGhostToggle(
            CommandDispatcher<S> dispatcher,
            Builder<S> builder,
            Function<CommandContext<S>, Source<S>> source
    ) {
        dispatcher.register(builder.literal("ghosttoggle")
                .requires(src -> ServerInfoManager.isOnWolfNetwork())
                .executes(context -> {
                    boolean enabled = GhostManagerClient.toggleEnabled();
                    source.apply(context).feedback(Text.literal(enabled
                            ? "[WolfMod] Ghost ativado."
                            : "[WolfMod] Ghost desativado."));
                    return 1;
                }));
    }

    private static <S> void registerCarPhysics(
            CommandDispatcher<S> dispatcher,
            Builder<S> builder,
            Function<CommandContext<S>, Source<S>> source
    ) {
        dispatcher.register(builder.literal("carphysics")
                .then(builder.literal("up").executes(context -> {
                    Source<S> src = source.apply(context);
                    CarPhysics.queueShift(src.playerUuid(), 1);
                    src.feedback(Text.literal("§e[Wolf] §fMarcha +"));
                    return 1;
                }))
                .then(builder.literal("down").executes(context -> {
                    Source<S> src = source.apply(context);
                    CarPhysics.queueShift(src.playerUuid(), -1);
                    src.feedback(Text.literal("§e[Wolf] §fMarcha -"));
                    return 1;
                }))
                .then(builder.literal("auto").executes(context -> {
                    Source<S> src = source.apply(context);
                    CarPhysics.setAuto(src.playerUuid(), true);
                    src.feedback(Text.literal("§e[Wolf] §aCâmbio AUTOMÁTICO."));
                    return 1;
                }))
                .then(builder.literal("manual").executes(context -> {
                    Source<S> src = source.apply(context);
                    CarPhysics.setAuto(src.playerUuid(), false);
                    src.feedback(Text.literal("§e[Wolf] §bCâmbio MANUAL §7(R/F ou /carphysics up|down)."));
                    return 1;
                }))
                .then(builder.word("state").executes(context -> {
                    Source<S> src = source.apply(context);
                    boolean on = parseCarPhysicsState(src, StringArgumentType.getString(context, "state"));
                    src.feedback(Text.literal(on
                            ? "§e[Wolf] §aModo carro ATIVADO."
                            : "§e[Wolf] §cModo carro DESATIVADO."));
                    return 1;
                }))
                .executes(context -> {
                    Source<S> src = source.apply(context);
                    boolean on = CarPhysics.toggle(src.playerUuid());
                    src.feedback(Text.literal(on
                            ? "§e[Wolf] §aModo carro ATIVADO."
                            : "§e[Wolf] §cModo carro DESATIVADO."));
                    return 1;
                }));
    }

    private static boolean parseCarPhysicsState(Source<?> source, String state) {
        UUID uuid = source.playerUuid();
        boolean on;
        if ("on".equalsIgnoreCase(state)) {
            on = true;
        } else if ("off".equalsIgnoreCase(state)) {
            on = false;
        } else if ("auto".equalsIgnoreCase(state)) {
            CarPhysics.setAuto(uuid, true);
            source.feedback(Text.literal("§e[Wolf] §aCâmbio AUTOMÁTICO."));
            return CarPhysics.isEnabled(uuid);
        } else if ("manual".equalsIgnoreCase(state)) {
            CarPhysics.setAuto(uuid, false);
            source.feedback(Text.literal("§e[Wolf] §bCâmbio MANUAL."));
            return CarPhysics.isEnabled(uuid);
        } else {
            source.feedback(Text.literal("§cUse: /carphysics [on|off|auto|manual|up|down]"));
            return CarPhysics.isEnabled(uuid);
        }
        CarPhysics.setEnabled(uuid, on);
        return on;
    }
}
