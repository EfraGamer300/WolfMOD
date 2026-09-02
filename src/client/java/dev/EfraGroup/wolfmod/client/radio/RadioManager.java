package dev.EfraGroup.wolfmod.client.radio;

import com.google.gson.JsonObject;
import dev.EfraGroup.wolfmod.network.RadioPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class RadioManager {
    private static volatile boolean recording = false;
    private static volatile boolean manuallyStopped = false;

    private static final AudioFormat DEFAULT_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final int SILENCE_TIMEOUT_MS = 1000;
    private static final int MAX_RECORDING_MS = 30000;

    public static void startRecording() {
        System.out.println("[RadioDebug] startRecording() chamado");
        if (recording) {
            System.out.println("[RadioDebug] Parando gravação manualmente");
            manuallyStopped = true;
            recording = false;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("§e[Rádio] §cParando..."), true);
                }
            });
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            System.out.println("[RadioDebug] Jogador é null, ignorando");
            return;
        }

        recording = true;
        manuallyStopped = false;
        System.out.println("[RadioDebug] recording = true");
        client.player.sendMessage(Text.literal("§e[Rádio] §aFale agora..."), true);
        System.out.println("[RadioDebug] ActionBar enviada: 'Fale agora...'");

        double threshold = RadioSettings.getThreshold();

        Thread thread = new Thread(() -> {
            TargetDataLine line = null;
            SourceDataLine speakerLine = null;
            NoiseSuppressor noiseSuppressor = new NoiseSuppressor();
            try {
                System.out.println("[RadioDebug] [Thread] Iniciando captura de áudio");
                line = openMicrophone();
                AudioFormat format = line.getFormat();
                System.out.println("[RadioDebug] [Thread] Microfone aberto: " + format);

                if (RadioSettings.isSidetoneEnabled()) {
                    speakerLine = openSidetoneLine(format);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                long startTime = System.currentTimeMillis();
                boolean speechDetected = false;
                long silenceSince = 0;
                int totalBytes = 0;

                System.out.println("[RadioDebug] [Thread] Aguardando fala... (threshold=" + threshold + ")");
                long lastLogTime = 0;
                while (recording && (System.currentTimeMillis() - startTime) < MAX_RECORDING_MS) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead <= 0) continue;

                    if (RadioSettings.isNoiseSuppressionEnabled()) {
                        noiseSuppressor.process(buffer, bytesRead);
                    }

                    double avgAmplitude = averageAmplitude(buffer, bytesRead);
                    boolean hasSpeech = avgAmplitude > threshold;

                    writeToSidetone(speakerLine, buffer, bytesRead);

                    long now = System.currentTimeMillis();
                    if (now - lastLogTime > 1000) {
                        lastLogTime = now;
                        System.out.println("[RadioDebug] [Thread] Amp média: " + String.format("%.1f", avgAmplitude) + " (threshold: " + threshold + ")");
                    }

                    if (hasSpeech) {
                        if (!speechDetected) {
                            speechDetected = true;
                            silenceSince = 0;
                            System.out.println("[RadioDebug] [Thread] Fala detectada!");
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.literal("§e[Rádio] §aFalando..."), true);
                                }
                            });
                        }
                        silenceSince = 0;
                        baos.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    } else if (speechDetected) {
                        baos.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        if (silenceSince == 0) {
                            silenceSince = System.currentTimeMillis();
                            System.out.println("[RadioDebug] [Thread] Silêncio detectado, aguardando " + SILENCE_TIMEOUT_MS + "ms...");
                        } else if (System.currentTimeMillis() - silenceSince >= SILENCE_TIMEOUT_MS) {
                            System.out.println("[RadioDebug] [Thread] " + SILENCE_TIMEOUT_MS + "ms de silêncio, encerrando");
                            break;
                        }
                    }
                }

                long elapsed = System.currentTimeMillis() - startTime;
                recording = false;
                System.out.println("[RadioDebug] recording = false");
                System.out.println("[RadioDebug] [Thread] Gravação encerrada após " + elapsed + "ms");
                System.out.println("[RadioDebug] [Thread] Total de bytes capturados: " + totalBytes);

                byte[] audioData = baos.toByteArray();
                System.out.println("[RadioDebug] [Thread] Audio data final: " + audioData.length + " bytes");

                float frameSize = format.getFrameSize();
                float sampleRate = format.getSampleRate();
                int durationMs = frameSize > 0 ? (int) (audioData.length / (sampleRate * frameSize / 1000.0)) : 0;
                System.out.println("[RadioDebug] [Thread] Duração do áudio: " + durationMs + "ms (formato: " + sampleRate + "Hz)");

                boolean shouldProcess = manuallyStopped || (speechDetected && durationMs >= 300);

                if (!shouldProcess) {
                    System.out.println("[RadioDebug] [Thread] Áudio muito curto ou sem fala, ignorando");
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.literal("§e[Rádio] §7Nada detectado."), true);
                        }
                    });
                } else {
                    saveAndProcess(audioData, format);
                }
            } catch (Exception e) {
                System.out.println("[RadioDebug] [Thread] EXCEÇÃO na captura: " + e.getMessage());
                e.printStackTrace();
                recording = false;
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendMessage(Text.literal("§c[Erro] " + e.getMessage()), true);
                    }
                });
            } finally {
                closeLines(speakerLine, line);
            }
        }, "Radio-Recording");
        thread.setDaemon(true);
        thread.start();
        System.out.println("[RadioDebug] Thread Radio-Recording iniciada");
    }

    private static TargetDataLine openMicrophone() throws LineUnavailableException {
        String mixerName = RadioSettings.getSelectedMixer();
        List<Mixer.Info> allMixers = Arrays.asList(AudioSystem.getMixerInfo());

        // Try selected mixer first, then all others
        List<Mixer.Info> candidates = new ArrayList<>();
        if (!mixerName.isEmpty()) {
            for (Mixer.Info mi : allMixers) {
                if (mi.getName().equals(mixerName)) {
                    candidates.add(mi);
                    break;
                }
            }
        }
        candidates.addAll(allMixers);

        for (Mixer.Info mi : candidates) {
            Mixer mixer = AudioSystem.getMixer(mi);

            // 1) native mono format closest to 16kHz
            AudioFormat nativeFmt = findBestFormat(mixer);
            if (nativeFmt != null) {
                DataLine.Info nativeInfo = new DataLine.Info(TargetDataLine.class, nativeFmt);
                if (mixer.isLineSupported(nativeInfo)) {
                    try {
                        TargetDataLine line = (TargetDataLine) mixer.getLine(nativeInfo);
                        line.open(nativeFmt);
                        line.start();
                        System.out.println("[RadioDebug] Microfone: " + mi.getName() + " nativo " + nativeFmt);
                        return line;
                    } catch (Exception e) {
                        // continue
                    }
                }
            }

            // 2) try 16kHz format directly
            DataLine.Info defaultInfo = new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT);
            if (mixer.isLineSupported(defaultInfo)) {
                try {
                    TargetDataLine line = (TargetDataLine) mixer.getLine(defaultInfo);
                    line.open(DEFAULT_FORMAT);
                    line.start();
                    System.out.println("[RadioDebug] Microfone: " + mi.getName() + " usando 16kHz");
                    return line;
                } catch (Exception e) {
                    // continue
                }
            }

            // 3) ANY mono format this mixer supports
            TargetDataLine line = tryAnyMonoFormat(mixer);
            if (line != null) {
                AudioFormat fmt = line.getFormat();
                System.out.println("[RadioDebug] Microfone: " + mi.getName() + " fallback " + fmt);
                return line;
            }

            // If we were looking for a specific mixer and it failed, don't try others
            if (!mixerName.isEmpty() && mi.getName().equals(mixerName)) break;
        }

        throw new LineUnavailableException("Nenhum microfone compatível encontrado");
    }

    private static TargetDataLine tryAnyMonoFormat(Mixer mixer) {
        Line.Info[] targetLines = mixer.getTargetLineInfo();
        for (Line.Info li : targetLines) {
            if (li instanceof DataLine.Info) {
                for (AudioFormat af : ((DataLine.Info) li).getFormats()) {
                    if (af.getChannels() == 1) {
                        try {
                            DataLine.Info afInfo = new DataLine.Info(TargetDataLine.class, af);
                            TargetDataLine line = (TargetDataLine) mixer.getLine(afInfo);
                            line.open(af);
                            line.start();
                            return line;
                        } catch (Exception e) {
                            // keep trying
                        }
                    }
                }
            }
        }
        return null;
    }

    private static AudioFormat findBestFormat(Mixer mixer) {
        Line.Info[] targetLines = mixer.getTargetLineInfo();
        AudioFormat best = null;
        for (Line.Info li : targetLines) {
            if (li instanceof DataLine.Info) {
                for (AudioFormat af : ((DataLine.Info) li).getFormats()) {
                    if (af.getChannels() == 1 && af.getSampleRate() >= 8000) {
                        float rate = af.getSampleRate();
                        int bits = af.getSampleSizeInBits();
                        if (bits > 0 && (bits != 8 || best == null)) { // prefer > 8-bit
                            if (best == null || Math.abs(rate - 16000) < Math.abs(best.getSampleRate() - 16000)) {
                                int targetBits = (bits == -1) ? 16 : bits;
                                best = new AudioFormat(rate, targetBits, 1, true, af.isBigEndian());
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    public static List<String> getAvailableMixers() {
        List<String> names = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mixer.isLineSupported(info) || findBestFormat(mixer) != null) {
                    names.add(mi.getName());
                }
            } catch (Exception e) {
                // skip invalid mixers
            }
        }
        return names;
    }

    private static double averageAmplitude(byte[] buffer, int bytesRead) {
        int samples = bytesRead / 2;
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            int low = buffer[i * 2] & 0xFF;
            int high = buffer[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            sum += Math.abs(sample);
        }
        return sum / samples;
    }

    private static void saveAndProcess(byte[] audioData, AudioFormat format) {
        System.out.println("[RadioDebug] saveAndProcess() chamado, audioData.length=" + audioData.length);
        MinecraftClient client = MinecraftClient.getInstance();

        try {
            Path saveDir = Path.of("config");
            Files.createDirectories(saveDir);
            Path wavPath = saveDir.resolve("f1_radio_temp.wav");
            System.out.println("[RadioDebug] Salvando WAV em: " + wavPath.toAbsolutePath());

            float frameSize = format.getFrameSize();
            if (frameSize <= 0) frameSize = format.getSampleSizeInBits() / 8 * format.getChannels();
            long numFrames = frameSize > 0 ? (long) (audioData.length / frameSize) : 0;
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream ais = new AudioInputStream(bais, format, numFrames);
            AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavPath.toFile());
            System.out.println("[RadioDebug] WAV salvo, tamanho no disco: " + Files.size(wavPath) + " bytes");

            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§e[Rádio] §cTransmissão encerrada. Processando áudio..."), true);
                    System.out.println("[RadioDebug] ActionBar: 'Transmissão encerrada. Processando áudio...'");
                }
            });

            System.out.println("[RadioDebug] Chamando NimApiClient.processAudio()...");
            NimApiClient.processAudio(wavPath).thenAccept(result -> {
                System.out.println("[RadioDebug] NimApiClient.processAudio() retornou");
                System.out.println("[RadioDebug] JSON recebido: " + result.toString());

                boolean valido = result.has("comando_valido") && result.get("comando_valido").getAsBoolean();
                String tipo = result.has("tipo_acao") ? result.get("tipo_acao").getAsString() : "ERRO";
                String valor = result.has("valor_acao") ? result.get("valor_acao").getAsString() : "ERRO";
                String audioLocal = result.has("audio_local") ? result.get("audio_local").getAsString() : "";

                System.out.println("[RadioDebug] Parse: valido=" + valido + ", tipo=" + tipo + ", valor=" + valor + ", audioLocal='" + audioLocal + "'");

                if (valido) {
                    System.out.println("[RadioDebug] Comando válido, executando ações...");
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                    Text.literal("§e[Rádio] §a" + tipo + ": " + valor), true
                            );
                            System.out.println("[RadioDebug] ActionBar: '" + tipo + ": " + valor + "'");
                        }

                        boolean canSend = ClientPlayNetworking.canSend(RadioPayload.ID);
                        System.out.println("[RadioDebug] canSend(RadioPayload)=" + canSend);
                        if (canSend) {
                            String payloadStr = tipo + ":" + valor;
                            System.out.println("[RadioDebug] Enviando RadioPayload: " + payloadStr);
                            ClientPlayNetworking.send(new RadioPayload(payloadStr));
                            System.out.println("[RadioDebug] RadioPayload enviado com sucesso");
                        } else {
                            System.out.println("[RadioDebug] Não pode enviar RadioPayload (canSend=false)");
                        }
                    });

                    if (!audioLocal.isEmpty()) {
                        System.out.println("[RadioDebug] Vai tocar áudio local: " + audioLocal);
                        playLocalAudio(audioLocal);
                    } else {
                        System.out.println("[RadioDebug] audioLocal vazio, pulando reprodução");
                    }
                } else {
                    System.out.println("[RadioDebug] Comando inválido ou erro");
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                    Text.literal("§e[Rádio] §c" + tipo + ": " + valor), true
                            );
                            System.out.println("[RadioDebug] ActionBar (erro): '" + tipo + ": " + valor + "'");
                        }
                    });
                }
            });

        } catch (Exception e) {
            System.out.println("[RadioDebug] EXCEÇÃO em saveAndProcess: " + e.getMessage());
            e.printStackTrace();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c[Erro] Falha ao processar áudio: " + e.getMessage()), true);
                }
            });
        }
    }

    private static void playLocalAudio(String filename) {
        ToneGenerator.ensureSoundsExist();
        Path audioPath = Path.of("config", "f1_radio", "sounds", filename);
        System.out.println("[RadioDebug] playLocalAudio() procurando: " + audioPath.toAbsolutePath());

        if (!Files.exists(audioPath)) {
            System.out.println("[RadioDebug] Arquivo de áudio não encontrado: " + audioPath);
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(
                            Text.literal("§e[Rádio] §cÁudio não encontrado: " + audioPath), true
                    );
                }
            });
            return;
        }

        System.out.println("[RadioDebug] Arquivo de áudio encontrado, iniciando reprodução...");
        Thread playbackThread = new Thread(() -> {
            try {
                AudioInputStream ais = AudioSystem.getAudioInputStream(audioPath.toFile());
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                System.out.println("[RadioDebug] Clip aberto, duração: " + clip.getMicrosecondLength() / 1000 + "ms");
                clip.start();
                System.out.println("[RadioDebug] Reprodução iniciada");
                clip.drain();
                System.out.println("[RadioDebug] Reprodução finalizada");
                clip.close();
                ais.close();
            } catch (Exception e) {
                System.out.println("[RadioDebug] EXCEÇÃO na reprodução de áudio: " + e.getMessage());
                e.printStackTrace();
            }
        }, "Radio-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
        System.out.println("[RadioDebug] Thread Radio-Playback iniciada");
    }

    private static SourceDataLine openSidetoneLine(AudioFormat format) {
        try {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine spk = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            spk.open(format);
            spk.start();
            System.out.println("[RadioDebug] Sidetone ativado: " + format);
            return spk;
        } catch (Exception e) {
            System.out.println("[RadioDebug] Sidetone nao disponivel: " + e.getMessage());
            return null;
        }
    }

    private static void writeToSidetone(SourceDataLine speakerLine, byte[] buffer, int bytesRead) {
        if (speakerLine == null) return;
        try {
            double vol = RadioSettings.getSidetoneVolume();
            if (vol < 1.0) {
                byte[] volBuf = buffer.clone();
                applyVolume(volBuf, bytesRead, vol);
                speakerLine.write(volBuf, 0, bytesRead);
            } else {
                speakerLine.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            System.out.println("[RadioDebug] Sidetone write error: " + e.getMessage());
        }
    }

    private static void closeLines(SourceDataLine speaker, TargetDataLine mic) {
        if (speaker != null) {
            try { speaker.stop(); } catch (Exception e) {}
            try { speaker.close(); } catch (Exception e) {}
        }
        if (mic != null) {
            try { mic.stop(); } catch (Exception e) {}
            try { mic.close(); } catch (Exception e) {}
        }
    }

    private static void applyVolume(byte[] buffer, int bytesRead, double volume) {
        if (volume >= 1.0) return;
        int samples = bytesRead / 2;
        for (int i = 0; i < samples; i++) {
            int low = buffer[i * 2] & 0xFF;
            int high = buffer[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            sample = (short) Math.max(-32768, Math.min(32767, Math.round(sample * volume)));
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    // --- Live monitoring for the config screen ---
    private static volatile boolean monitoring = false;
    private static volatile double monitorAmplitude = 0;
    private static Thread monitorThread = null;

    public static double getMonitorAmplitude() {
        return monitorAmplitude;
    }

    public static boolean isMonitoring() {
        return monitoring;
    }

    public static void startMonitor() {
        if (monitoring || recording) return;
        waitForMonitorThread();
        monitoring = true;
        monitorAmplitude = 0;

        monitorThread = new Thread(() -> {
            TargetDataLine line = null;
            SourceDataLine speakerLine = null;
            NoiseSuppressor noiseSuppressor = new NoiseSuppressor();
            try {
                line = openMicrophone();
                AudioFormat format = line.getFormat();
                speakerLine = openSidetoneLine(format);

                byte[] buf = new byte[2048];
                while (monitoring) {
                    int read = line.read(buf, 0, buf.length);
                    if (read <= 0) continue;

                    if (RadioSettings.isNoiseSuppressionEnabled()) {
                        noiseSuppressor.process(buf, read);
                    }

                    monitorAmplitude = averageAmplitude(buf, read);

                    writeToSidetone(speakerLine, buf, read);
                }
            } catch (Exception e) {
                System.out.println("[RadioDebug] Monitor EXCEÇÃO: " + e.getMessage());
                monitoring = false;
            } finally {
                closeLines(speakerLine, line);
            }
        }, "Radio-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private static void waitForMonitorThread() {
        Thread old = monitorThread;
        if (old != null && old.isAlive()) {
            monitoring = false;
            try {
                old.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        monitorThread = null;
        monitorAmplitude = 0;
    }

    public static void stopMonitor() {
        monitoring = false;
        monitorAmplitude = 0;
        monitorThread = null;
    }
}
