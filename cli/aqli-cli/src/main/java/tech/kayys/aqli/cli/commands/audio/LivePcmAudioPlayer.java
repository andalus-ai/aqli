package tech.kayys.aqli.cli.commands.audio;

import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Best-effort Java Sound playback for live PCM chunks produced by a streaming audio response.
 *
 * <p>This is a CLI output adapter: device selection, playback availability, and warnings are
 * intentionally kept out of the reusable audio runtime.</p>
 */
public final class LivePcmAudioPlayer implements AutoCloseable {
    private final boolean enabled;
    private SourceDataLine line;
    private AudioFormat format;
    private boolean warned;

    public LivePcmAudioPlayer(boolean enabled) {
        this.enabled = enabled;
    }

    public void write(byte[] pcm, Map<String, Object> metadata) {
        if (!enabled || pcm == null || pcm.length == 0) {
            return;
        }
        try {
            ensureOpen(metadata);
            if (line != null) {
                line.write(pcm, 0, pcm.length);
            }
        } catch (Exception e) {
            warnOnce(e);
        }
    }

    private void ensureOpen(Map<String, Object> metadata) throws Exception {
        int sampleRate = (int) Math.round(optionalMetadataDouble(metadata, "audio_sample_rate", 48_000.0));
        int channels = Math.max(1, (int) Math.round(optionalMetadataDouble(metadata, "audio_channels", 1.0)));
        AudioFormat requested = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * Short.BYTES,
                sampleRate,
                false);
        if (line != null && requested.matches(format)) {
            return;
        }
        closeLine();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, requested);
        line = (SourceDataLine) AudioSystem.getLine(info);
        int bufferSize = Math.max(requested.getFrameSize(), sampleRate * requested.getFrameSize() / 4);
        line.open(requested, bufferSize);
        line.start();
        format = requested;
    }

    private static double optionalMetadataDouble(
            Map<String, Object> metadata,
            String key,
            double fallback) {
        if (metadata == null || metadata.isEmpty()) {
            return fallback;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            return parsed > 0.0 ? parsed : fallback;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                double parsed = Double.parseDouble(text.trim());
                return parsed > 0.0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        System.err.println("\nWarning: live audio preview unavailable: " + e.getMessage());
    }

    @Override
    public void close() {
        closeLine();
    }

    private void closeLine() {
        if (line == null) {
            return;
        }
        try {
            line.drain();
            line.stop();
            line.close();
        } catch (Exception ignored) {
        } finally {
            line = null;
            format = null;
        }
    }
}
