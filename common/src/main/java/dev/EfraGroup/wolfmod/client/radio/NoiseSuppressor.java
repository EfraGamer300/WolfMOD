package dev.EfraGroup.wolfmod.client.radio;

public class NoiseSuppressor {
    private double prevIn = 0;
    private double prevOut = 0;
    private static final double HPF_ALPHA = 0.98;

    private double noiseFloor = 0;
    private boolean initialized = false;
    private static final double NOISE_UPDATE_RATE = 0.02;
    private static final double NOISE_FLOOR_MULTIPLIER = 1.8;

    private static final double KNEE_LOW = 0.25;
    private static final double KNEE_HIGH = 1.2;

    public void process(byte[] buffer, int bytesRead) {
        int samples = bytesRead / 2;

        for (int i = 0; i < samples; i++) {
            int low = buffer[i * 2] & 0xFF;
            int high = buffer[i * 2 + 1];
            double sample = (short) ((high << 8) | low);

            double filtered = sample - prevIn + HPF_ALPHA * prevOut;
            prevIn = sample;
            prevOut = filtered;

            double absVal = Math.abs(filtered);

            if (!initialized) {
                noiseFloor = Math.max(absVal, 1.0);
                initialized = true;
            }

            if (absVal < noiseFloor * NOISE_FLOOR_MULTIPLIER) {
                noiseFloor += (absVal - noiseFloor) * NOISE_UPDATE_RATE;
            } else {
                noiseFloor += (0 - noiseFloor) * NOISE_UPDATE_RATE * 0.05;
            }
            noiseFloor = Math.max(noiseFloor, 1.0);

            double snr = absVal / noiseFloor;

            double gain;
            if (snr <= KNEE_LOW) {
                gain = 0.0;
            } else if (snr >= KNEE_HIGH) {
                gain = 1.0;
            } else {
                double t = (snr - KNEE_LOW) / (KNEE_HIGH - KNEE_LOW);
                gain = t * t * (3 - 2 * t);
            }

            double output = filtered * gain;

            short outSample = (short) Math.max(-32768, Math.min(32767, Math.round(output)));
            buffer[i * 2] = (byte) (outSample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((outSample >> 8) & 0xFF);
        }
    }

    public void reset() {
        prevIn = 0;
        prevOut = 0;
        noiseFloor = 0;
        initialized = false;
    }
}
