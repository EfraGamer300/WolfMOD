package dev.EfraGroup.wolfmod.fabric;

import dev.EfraGroup.wolfmod.client.GhostConfig;
import dev.EfraGroup.wolfmod.client.ghost.GhostBoatType;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Config screen backed by Yet Another Config Lib, opened from Mod Menu.
 *
 * <p>Every option writes straight into the {@link GhostConfig} singleton and the whole file is
 * flushed by the YACL save hook, so the game does not need a restart.
 */
public final class WolfConfigScreen {
    private WolfConfigScreen() {
    }

    public static Screen create(Screen parent) {
        GhostConfig config = GhostConfig.get();
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("WolfMOD"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Cronômetro"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("HUD"))
                                .option(toggle("Mostrar cronômetro",
                                        "Mostra o painel do tempo de volta durante a corrida.",
                                        () -> config.useTimeTrialTimer,
                                        value -> config.useTimeTrialTimer = value))
                                .option(percentage("Posição vertical",
                                        "Altura do painel na tela (0% topo, 100% base).",
                                        () -> config.timeTrialTimerYPosition,
                                        value -> config.timeTrialTimerYPosition = value))
                                .option(scale("Escala",
                                        "Tamanho do painel do cronômetro.",
                                        () -> config.timeTrialTimerScale,
                                        value -> config.timeTrialTimerScale = value))
                                .option(percentage("Opacidade do fundo",
                                        "Transparência do fundo do painel.",
                                        () -> config.timeTrialTimerBackgroundOpacity,
                                        value -> config.timeTrialTimerBackgroundOpacity = value))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Ghost"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Personal best"))
                                .option(toggle("Mostrar ghost",
                                        "Renderiza o barco e o piloto da sua melhor volta.",
                                        () -> config.renderPersonalBestGhost,
                                        value -> config.renderPersonalBestGhost = value))
                                .option(toggle("Mostrar piloto",
                                        "Renderiza o piloto fantasma.",
                                        () -> config.ghostPlayerVisible,
                                        value -> config.ghostPlayerVisible = value))
                                .option(boatType(() -> GhostBoatType.fromId(config.ghostBoatType),
                                        value -> config.ghostBoatType = value.id))
                                .option(percentage("Opacidade do ghost",
                                        "Transparência do barco/piloto fantasma.",
                                        () -> config.ghostOpacity,
                                        value -> config.ghostOpacity = value))
                                .option(playbackOffset(() -> config.ghostPlaybackOffset,
                                        value -> config.ghostPlaybackOffset = value))
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Linha do personal best"))
                                .option(toggle("Mostrar linha",
                                        "Desenha a trajetória da melhor volta no chão.",
                                        () -> config.renderPersonalBestLine,
                                        value -> config.renderPersonalBestLine = value))
                                .option(lineColor(() -> new Color(config.lineColor, true),
                                        value -> config.lineColor = value.getRGB()))
                                .build())
                        .build())
                .save(config::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(
            String name,
            String description,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {
        return Option.<Boolean>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(description)))
                .binding(getter.get(), getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Float> percentage(
            String name,
            String description,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        return Option.<Float>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(description)))
                .binding(getter.get() * 100.0f,
                        () -> getter.get() * 100.0f,
                        value -> setter.accept(value / 100.0f))
                .controller(option -> FloatSliderControllerBuilder.create(option)
                        .range(0.0f, 100.0f)
                        .step(1.0f)
                        .formatValue(value -> Text.literal(String.format(Locale.ROOT, "%.0f%%", value))))
                .build();
    }

    private static Option<Float> scale(
            String name,
            String description,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        return Option.<Float>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(description)))
                .binding(getter.get(), getter, setter)
                .controller(option -> FloatSliderControllerBuilder.create(option)
                        .range(0.5f, 2.0f)
                        .step(0.05f)
                        .formatValue(value -> Text.literal(String.format(Locale.ROOT, "%.2fx", value))))
                .build();
    }

    private static Option<GhostBoatType> boatType(
            Supplier<GhostBoatType> getter,
            Consumer<GhostBoatType> setter
    ) {
        return Option.<GhostBoatType>createBuilder()
                .name(Text.literal("Barco do ghost"))
                .description(OptionDescription.of(Text.literal("Modelo de barco usado pelo ghost.")))
                .binding(getter.get(), getter, setter)
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(GhostBoatType.class)
                        .formatValue(value -> Text.literal(value.name())))
                .build();
    }

    private static Option<Integer> playbackOffset(
            Supplier<Integer> getter,
            Consumer<Integer> setter
    ) {
        return Option.<Integer>createBuilder()
                .name(Text.literal("Offset do playback"))
                .description(OptionDescription.of(Text.literal(
                        "Antecipação do ghost em ticks: positivo começa antes, negativo depois.")))
                .binding(getter.get(), getter, setter)
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(-100, 100)
                        .step(1)
                        .formatValue(value -> Text.literal(value + " ticks")))
                .build();
    }

    private static Option<Color> lineColor(
            Supplier<Color> getter,
            Consumer<Color> setter
    ) {
        return Option.<Color>createBuilder()
                .name(Text.literal("Cor da linha"))
                .description(OptionDescription.of(Text.literal("Cor da trajetória do personal best.")))
                .binding(getter.get(), getter, setter)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(true))
                .build();
    }
}
