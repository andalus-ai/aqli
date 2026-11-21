package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.cli.commands.generation.GenerationSeedRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageGenerationRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageMetadataRunOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaMetadataSidecarRequestFactoryTest {

    @Test
    void imageRequestCombinesRunContextAndMetadataSeed() {
        var request = MediaMetadataSidecarRequestFactory.image(
                Path.of("generated.png"),
                Map.of("image_seed", 91),
                new ImageMetadataRunOptions("image.json", true, null, null),
                "model-id",
                "provider-id",
                "runner-id",
                "prompt text",
                new ImageGenerationRunOptions(24, 6.5f, 768, 512),
                new GenerationSeedRunOptions(12L));

        assertEquals(Path.of("generated.png"), request.imagePath());
        assertEquals("image.json", request.imageMetadataOutputPath());
        assertEquals(true, request.imageMetadataSidecar());
        assertEquals("model-id", request.modelId());
        assertEquals("provider-id", request.providerId());
        assertEquals("runner-id", request.runner());
        assertEquals("prompt text", request.prompt());
        assertEquals("91", request.replaySeed());
        assertEquals(24, request.steps());
        assertEquals(6.5f, request.guidanceScale());
        assertEquals(768, request.width());
        assertEquals(512, request.height());
    }

    @Test
    void imageRequestUsesRunSeedWhenMetadataHasNoSeed() {
        var request = MediaMetadataSidecarRequestFactory.image(
                Path.of("generated.png"),
                Map.of(),
                new ImageMetadataRunOptions(null, false, null, null),
                "model-id",
                "provider-id",
                "runner-id",
                "prompt text",
                new ImageGenerationRunOptions(null, null, null, null),
                new GenerationSeedRunOptions(12L));

        assertEquals("12", request.replaySeed());
    }
}
