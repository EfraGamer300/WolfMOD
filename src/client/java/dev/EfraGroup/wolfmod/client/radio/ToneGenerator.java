package dev.EfraGroup.wolfmod.client.radio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class ToneGenerator {

    private static final int SAMPLE_RATE = 16000;
    private static final int BITS = 16;
    private static final int CHANNELS = 1;
    private static final int DURATION_MS = 300;

    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false);

    public static void ensureSoundsExist() {
        Path soundsDir = Path.of("config", "f1_radio", "sounds");
        try {
            Files.createDirectories(soundsDir);

            /* tones:  pitch    label
             *        200 Hz  - box_hard      (grave)
             *        350 Hz  - box_medium
             *        500 Hz  - box_soft      (agudo)
             *        ---------------------------------
             *        440 Hz  - box           (Lá padrão)
             *        600 Hz  - confirm       (beep agudo)
             *        250 Hz  - negative      (buzina grave)
             *        550 Hz  - engine_ok     (tom médio-alto)
             *        800 Hz  - radio_check   (beep fino)
             *        380 Hz  - undercut      (médio-grave)
             *        420 Hz  - stay_out      (médio)
             *        480 Hz  - front_wing    (médio-agudo)
             *        520 Hz  - rear_wing     (médio-agudo+)
             *        660 Hz  - drs_open      (agudo)
             *        300 Hz  - drs_closed    (grave)
             */
            generateIfMissing(soundsDir.resolve("box_hard.wav"), 200);
            generateIfMissing(soundsDir.resolve("box_medium.wav"), 350);
            generateIfMissing(soundsDir.resolve("box_soft.wav"), 500);
            generateIfMissing(soundsDir.resolve("box.wav"), 440);
            generateIfMissing(soundsDir.resolve("confirm.wav"), 600);
            generateIfMissing(soundsDir.resolve("negative.wav"), 250);
            generateIfMissing(soundsDir.resolve("engine_ok.wav"), 550);
            generateIfMissing(soundsDir.resolve("radio_check.wav"), 800);
            generateIfMissing(soundsDir.resolve("undercut.wav"), 380);
            generateIfMissing(soundsDir.resolve("stay_out.wav"), 420);
            generateIfMissing(soundsDir.resolve("front_wing.wav"), 480);
            generateIfMissing(soundsDir.resolve("rear_wing.wav"), 520);
            generateIfMissing(soundsDir.resolve("drs_open.wav"), 660);
            generateIfMissing(soundsDir.resolve("drs_closed.wav"), 300);
        } catch (Exception e) {
            System.out.println("[RadioDebug] ERRO gerando sons: " + e.getMessage());
        }
    }

    private static void generateIfMissing(Path path, int freqHz) throws Exception {
        if (Files.exists(path)) {
            System.out.println("[RadioDebug] Som já existe: " + path.getFileName());
            return;
        }
        System.out.println("[RadioDebug] Gerando som: " + path.getFileName() + " (" + freqHz + "Hz)");

        int totalSamples = SAMPLE_RATE * DURATION_MS / 1000;
        byte[] buffer = new byte[totalSamples * 2];
        double volume = 0.8;

        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = 1.0;
            if (i < SAMPLE_RATE / 100) {
                envelope = (double) i / (SAMPLE_RATE / 100);
            }
            if (i > totalSamples - SAMPLE_RATE / 100) {
                envelope = (double) (totalSamples - i) / (SAMPLE_RATE / 100);
            }
            double sample = Math.sin(2 * Math.PI * freqHz * t) * volume * envelope;
            short s = (short) (sample * Short.MAX_VALUE);
            buffer[i * 2] = (byte) (s & 0xFF);
            buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        AudioInputStream ais = new AudioInputStream(bais, FORMAT, totalSamples);
        AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        System.out.println("[RadioDebug] Som gerado: " + path.getFileName() + " (" + Files.size(path) + " bytes)");
    }
}
