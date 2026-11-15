package tech.kayys.aqli.cli.commands.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class ImageGenerationRunRequestParameterMapperTest {

    @Test
    void mapsImageGenerationDimensionsOutputGuidanceAndPipeline() {
        InferenceRequest request = request(new ImageGenerationRunRequestParameterMapper.Options(
                "  diffusion-xl  ", 7.5, "/tmp/generated.png", 1024, 768));

        Map<String, Object> parameters = request.getParameters();
        assertEquals("diffusion-xl", parameters.get("pipeline"));
        assertEquals("diffusion-xl", parameters.get("feature"));
        assertEquals(true, parameters.get("feature_pipeline"));
        assertEquals(7.5, parameters.get("guidance_scale"));
        assertEquals("/tmp/generated.png", parameters.get("output_path"));
        assertEquals(1024, parameters.get("width"));
        assertEquals(768, parameters.get("height"));
        assertFalse(parameters.containsKey("image_path"));
    }

    @Test
    void omitsUnsetImageGenerationOptions() {
        InferenceRequest request = request(new ImageGenerationRunRequestParameterMapper.Options(
                null, null, null, null, null));

        assertEquals(Map.of(), request.getParameters());
    }

    @Test
    void preservesBlankPipelineAsAbsentWithoutDroppingOtherOptions() {
        InferenceRequest request = request(new ImageGenerationRunRequestParameterMapper.Options(
                "   ", 3.0, null, 512, null));

        Map<String, Object> parameters = request.getParameters();
        assertFalse(parameters.containsKey("pipeline"));
        assertFalse(parameters.containsKey("feature"));
        assertFalse(parameters.containsKey("feature_pipeline"));
        assertEquals(3.0, parameters.get("guidance_scale"));
        assertEquals(512, parameters.get("width"));
    }

    @Test
    void rejectsMissingRequestOrOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageGenerationRunRequestParameterMapper.apply(null,
                        new ImageGenerationRunRequestParameterMapper.Options(null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> ImageGenerationRunRequestParameterMapper.apply(InferenceRequest.builder(),
                        null));
    }

    private static InferenceRequest request(ImageGenerationRunRequestParameterMapper.Options options) {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model("test-model")
                .prompt("generate an image");
        ImageGenerationRunRequestParameterMapper.apply(builder, options);
        return builder.build();
    }
}
