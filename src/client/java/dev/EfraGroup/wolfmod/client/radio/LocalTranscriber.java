package dev.EfraGroup.wolfmod.client.radio;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LocalTranscriber {

    private static final Path MODEL_DIR = Path.of("config", "f1_radio", "vosk-model-small-pt-0.3");
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip";
    private static final Path ZIP_PATH = Path.of("config", "f1_radio", "vosk-model-small-pt-0.3.zip");
    private static Model model;
    private static boolean initialized = false;

    public static synchronized String transcribe(Path wavPath) throws Exception {
        if (!initialized) {
            ensureModel();
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(MODEL_DIR.toAbsolutePath().toString());
            initialized = true;
            System.out.println("[RadioDebug] Vosk inicializado com modelo: " + MODEL_DIR);
        }

        byte[] wavBytes = Files.readAllBytes(wavPath);
        System.out.println("[RadioDebug] [Vosk] WAV lido: " + wavBytes.length + " bytes");

        // WAV header: sample rate at bytes 24-27 (little-endian int32)
        int sampleRate = 16000;
        if (wavBytes.length >= 28) {
            sampleRate = (wavBytes[24] & 0xFF)
                       | ((wavBytes[25] & 0xFF) << 8)
                       | ((wavBytes[26] & 0xFF) << 16)
                       | ((wavBytes[27] & 0xFF) << 24);
        }
        System.out.println("[RadioDebug] [Vosk] Sample rate detectado: " + sampleRate + " Hz");

        try (Recognizer recognizer = new Recognizer(model, sampleRate)) {
            // Skip WAV header (44 bytes) and feed raw PCM
            int dataOffset = 44;
            if (wavBytes.length > dataOffset) {
                byte[] pcmData = Arrays.copyOfRange(wavBytes, dataOffset, wavBytes.length);
                recognizer.acceptWaveForm(pcmData, pcmData.length);
            }
            String result = recognizer.getFinalResult();
            System.out.println("[RadioDebug] [Vosk] Resultado bruto: " + result);

            String text = extractText(result);
            System.out.println("[RadioDebug] [Vosk] Texto extraído: '" + text + "'");
            return text;
        }
    }

    private static String extractText(String voskJson) {
        try {
            int textIdx = voskJson.indexOf("\"text\"");
            if (textIdx < 0) return "";
            int colonIdx = voskJson.indexOf(':', textIdx);
            int startIdx = voskJson.indexOf('"', colonIdx + 1);
            int endIdx = voskJson.indexOf('"', startIdx + 1);
            if (startIdx < 0 || endIdx < 0) return "";
            return voskJson.substring(startIdx + 1, endIdx);
        } catch (Exception e) {
            System.out.println("[RadioDebug] [Vosk] Erro parseando JSON: " + e.getMessage());
            return "";
        }
    }

    private static void ensureModel() throws Exception {
        if (Files.exists(MODEL_DIR) && Files.isDirectory(MODEL_DIR)) {
            System.out.println("[RadioDebug] [Vosk] Modelo já existe em: " + MODEL_DIR);
            return;
        }

        System.out.println("[RadioDebug] [Vosk] Baixando modelo português... (" + MODEL_URL + ")");
        System.out.println("[RadioDebug] [Vosk] Isso pode levar alguns minutos na primeira execução.");

        Files.createDirectories(ZIP_PATH.getParent());

        try (InputStream in = new URL(MODEL_URL).openStream();
             FileOutputStream fos = new FileOutputStream(ZIP_PATH.toFile())) {
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            long lastLog = 0;
            while ((len = in.read(buf)) > 0) {
                fos.write(buf, 0, len);
                total += len;
                if (System.currentTimeMillis() - lastLog > 5000) {
                    System.out.println("[RadioDebug] [Vosk] Baixando... " + (total / 1024 / 1024) + " MB");
                    lastLog = System.currentTimeMillis();
                }
            }
        }

        System.out.println("[RadioDebug] [Vosk] Download completo, extraindo...");
        extractZip(ZIP_PATH, MODEL_DIR.getParent());
        Files.deleteIfExists(ZIP_PATH);

        if (!Files.exists(MODEL_DIR) || !Files.isDirectory(MODEL_DIR)) {
            throw new RuntimeException("Falha ao extrair modelo Vosk: diretório não encontrado em " + MODEL_DIR);
        }

        System.out.println("[RadioDebug] [Vosk] Modelo extraído em: " + MODEL_DIR);
    }

    private static void extractZip(Path zipPath, Path destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
