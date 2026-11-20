package tech.kayys.aqli.cli.commands.audio;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioRunStatsRendererTest {
    @Test
    void rendersAudioDurationAndDerivedRealtimeMetrics() {
        Map<String, Object> metadata = Map.of(
                "audio_samples", 48_000,
                "audio_sample_rate", 24_000,
                "audio_generation_duration_seconds", 1.0,
                "tts_frames", 50);
        assertEquals("\n[Audio: 2.00s, Generation: 1.00s, Speed: 2.00x realtime, RTF: 0.50, Synthesis: 50.0 frames/s]",
                AudioRunStatsRenderer.render(metadata, 900).orElseThrow());
    }

    @Test
    void prefersExplicitRealtimeMetrics() {
        Map<String, Object> metadata = Map.of(
                "audio_duration_seconds", 3.0,
                "audio_generation_duration_seconds", 2.0,
                "audio_realtime_speed", 1.25,
                "audio_realtime_factor", 0.8);
        assertEquals("\n[Audio: 3.00s, Generation: 2.00s, Speed: 1.25x realtime, RTF: 0.80]",
                AudioRunStatsRenderer.render(metadata, 900).orElseThrow());
    }

    @Test
    void usesLatencyThenFallbackDurationForGenerationTime() {
        assertEquals("\n[Generation: 0.75s]",
                AudioRunStatsRenderer.render(Map.of("bench.latency_ms", 750), 900).orElseThrow());
        assertEquals("\n[Generation: 0.90s]",
                AudioRunStatsRenderer.render(Map.of(), 900).orElseThrow());
    }

    @Test
    void identifiesAudioMetadataAndOmitsEmptyStats() {
        assertTrue(AudioRunStatsRenderer.isAudioMetadata(Map.of("audio_format", "flac")));
        assertTrue(AudioRunStatsRenderer.isAudioMetadata(Map.of("audio_duration_seconds", 1.0)));
        assertTrue(AudioRunStatsRenderer.isAudioMetadata(Map.of("tts_frames", 10)));
        assertFalse(AudioRunStatsRenderer.isAudioMetadata(Map.of("tokens.output", 10)));
        assertFalse(AudioRunStatsRenderer.isAudioMetadata(null));
        assertTrue(AudioRunStatsRenderer.render(Map.of(), 0).isEmpty());
    }
}
