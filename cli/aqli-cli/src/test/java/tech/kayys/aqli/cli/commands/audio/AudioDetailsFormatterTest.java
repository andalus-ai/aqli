package tech.kayys.aqli.cli.commands.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AudioDetailsFormatterTest {
    @Test
    void formatsDurationAndAudioProperties() {
        assertEquals("2.50s, 48000 Hz, 2ch, 192 kbps",
                AudioDetailsFormatter.format(Map.of(
                        "audio_duration_seconds", 2.5,
                        "audio_sample_rate", 48_000,
                        "audio_channels", 2,
                        "audio_bitrate_kbps", 192)));
    }

    @Test
    void derivesDurationFromSampleCountWhenDurationIsAbsent() {
        assertEquals("1.50s, 24000 Hz",
                AudioDetailsFormatter.format(Map.of("audio_samples", 36_000, "audio_sample_rate", 24_000)));
    }

    @Test
    void formatsVoiceSeedAndProcessingMetadata() {
        assertEquals("polished peak -1.2 dBFS, trimmed 120 ms, voice aria (auto en), seed 42 (generated)",
                AudioDetailsFormatter.format(Map.of(
                        "audio_processing", true,
                        "audio_processing_peak_after_dbfs", "-1.2",
                        "audio_processing_trim_silence", true,
                        "audio_processing_trim_removed_ms", 120,
                        "tts_voice", "aria",
                        "tts_voice_mode", "auto",
                        "tts_voice_language", "en",
                        "tts_seed", 42,
                        "tts_seed_source", "generated")));
    }

    @Test
    void formatsTtsStopReasonAndOnnxCacheState() {
        assertEquals("3 frames (duration cap), onnx cache 2/3 hit (1 built), onnx sessions hot, onnx assets warmed",
                AudioDetailsFormatter.format(Map.of(
                        "tts_frames", 3,
                        "tts_stop_reason", "max_seconds",
                        "onnx_optimized_model_cache_sessions", 3,
                        "onnx_optimized_model_cache_hits", 2,
                        "onnx_optimized_model_cache_created", 1,
                        "onnx_session_cache_state", "hit",
                        "onnx_asset_cache_state", "miss")));
    }

    @Test
    void returnsNullWhenNoDisplayableMetadataExists() {
        assertNull(AudioDetailsFormatter.format(null));
        assertNull(AudioDetailsFormatter.format(Map.of()));
        assertNull(AudioDetailsFormatter.format(Map.of("unrelated", "value")));
    }
}
