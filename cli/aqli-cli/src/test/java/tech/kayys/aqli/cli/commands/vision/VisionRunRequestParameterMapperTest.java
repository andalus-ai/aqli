package tech.kayys.aqli.cli.commands.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class VisionRunRequestParameterMapperTest {

    @Test
    void mapsAllImageAliasesAndUsesVisionTaskForMultipleImages() {
        InferenceRequest request = request(List.of("/tmp/one.png", "/tmp/two.png"), false, null, false);

        Map<String, Object> parameters = request.getParameters();
        assertEquals("/tmp/one.png", parameters.get("image_path"));
        assertEquals("/tmp/one.png", parameters.get("input_image"));
        assertEquals("/tmp/one.png", parameters.get("vision_input_path"));
        assertEquals(List.of("/tmp/one.png", "/tmp/two.png"), parameters.get("image_paths"));
        assertEquals(List.of("/tmp/one.png", "/tmp/two.png"), parameters.get("vision_input_paths"));
        assertEquals("vision", parameters.get("task"));
        assertFalse(parameters.containsKey("ocr"));
    }

    @Test
    void ocrModeWinsWhenNoImageIsProvidedAndClampsNegativeTokenCount() {
        InferenceRequest request = request(List.of(), true, -4, true);

        Map<String, Object> parameters = request.getParameters();
        assertEquals("ocr", parameters.get("task"));
        assertEquals(true, parameters.get("ocr"));
        assertEquals(0, parameters.get("ocr_image_tokens"));
        assertEquals(true, parameters.get("ocr_raw_output"));
        assertFalse(parameters.containsKey("image_path"));
    }

    @Test
    void doesNotAddVisionTaskForTextOnlyRequest() {
        InferenceRequest request = request(List.of(), false, null, false);

        assertNull(request.getParameters().get("task"));
        assertFalse(request.getParameters().containsKey("ocr_raw_output"));
    }

    private static InferenceRequest request(
            List<String> images, boolean ocr, Integer imageTokens, boolean rawOutput) {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model("test-model")
                .prompt("describe this");
        VisionRunRequestParameterMapper.apply(
                builder,
                new VisionRunRequestParameterMapper.Options(images, ocr, imageTokens, rawOutput));
        return builder.build();
    }
}
