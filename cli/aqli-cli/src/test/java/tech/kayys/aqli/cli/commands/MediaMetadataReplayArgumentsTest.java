package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaMetadataReplayArgumentsTest {

    @Test
    void audioSeedPrefersTtsSeedAndFallsBackToRunSeed() {
        assertEquals("metadata-seed", MediaMetadataReplayArguments.audioSeed(
                Map.of("tts_seed", 42, "seed", 7), "run-seed"));
        assertEquals("run-seed", MediaMetadataReplayArguments.audioSeed(Map.of(), "run-seed"));
        assertEquals(null, MediaMetadataReplayArguments.audioSeed(null, null));
    }

    @Test
    void imageSeedUsesImageSpecificSeedBeforeGenericSeed() {
        assertEquals("image-seed", MediaMetadataReplayArguments.imageSeed(
                Map.of("image_seed", "image-seed", "seed", "generic-seed"), "run-seed"));
        assertEquals("generic-seed", MediaMetadataReplayArguments.imageSeed(
                Map.of("seed", "generic-seed"), "run-seed"));
        assertEquals("run-seed", MediaMetadataReplayArguments.imageSeed(Map.of(), "run-seed"));
    }

    @Test
    void blankMetadataSeedDoesNotOverrideFallback() {
        assertEquals("run-seed", MediaMetadataReplayArguments.imageSeed(
                Map.of("image_seed", "  ", "seed", ""), "run-seed"));
    }
}
