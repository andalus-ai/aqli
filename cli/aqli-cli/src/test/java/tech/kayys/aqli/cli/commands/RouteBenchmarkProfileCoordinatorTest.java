package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBenchmarkProfileCoordinatorTest {

    @Test
    void recordsNonAudioOutput() {
        assertTrue(RouteBenchmarkProfileCoordinator.shouldRecord(
                Map.of("tokens_per_second", 12.5),
                false,
                RouteBenchmarkProfileCoordinatorTest::isAudioMetadata));
    }

    @Test
    void skipsExplicitAudioOutput() {
        assertFalse(RouteBenchmarkProfileCoordinator.shouldRecord(
                Map.of("tokens_per_second", 12.5),
                true,
                RouteBenchmarkProfileCoordinatorTest::isAudioMetadata));
    }

    @Test
    void skipsAudioMetadataEvenWhenOutputFlagIsFalse() {
        assertFalse(RouteBenchmarkProfileCoordinator.shouldRecord(
                Map.of("audio_format", "wav"),
                false,
                RouteBenchmarkProfileCoordinatorTest::isAudioMetadata));
    }

    @Test
    void skipsTtsFrameMetadata() {
        assertFalse(RouteBenchmarkProfileCoordinator.shouldRecord(
                Map.of("tts_frames", 42),
                false,
                RouteBenchmarkProfileCoordinatorTest::isAudioMetadata));
    }

    private static boolean isAudioMetadata(Map<String, Object> metadata) {
        return metadata != null
                && (metadata.containsKey("audio_format")
                || metadata.containsKey("audio_duration_seconds")
                || metadata.containsKey("tts_frames"));
    }
}
