package tech.kayys.aqli.cli.commands.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class ModelSourceRunRequestParameterMapperTest {

    @Test
    void mapsCustomLocalPathAndRequestedModelReference() {
        InferenceRequest request = request(new ModelSourceRunRequestParameterMapper.Options(
                true, "/models/custom/model.gguf", "hf:org/model"));

        Map<String, Object> parameters = request.getParameters();
        assertEquals("/models/custom/model.gguf", parameters.get("model_path"));
        assertEquals("hf:org/model", parameters.get("requested_model_ref"));
    }

    @Test
    void omitsLocalPathWhenCustomPathWasNotSelected() {
        InferenceRequest request = request(new ModelSourceRunRequestParameterMapper.Options(
                false, "/models/resolved/model.gguf", "hf:org/model"));

        Map<String, Object> parameters = request.getParameters();
        assertFalse(parameters.containsKey("model_path"));
        assertEquals("hf:org/model", parameters.get("requested_model_ref"));
    }

    @Test
    void omitsBlankRequestedModelReferenceAndNullLocalPath() {
        InferenceRequest request = request(new ModelSourceRunRequestParameterMapper.Options(
                true, null, "  "));

        assertEquals(Map.of(), request.getParameters());
    }

    @Test
    void rejectsMissingRequestOrOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelSourceRunRequestParameterMapper.apply(null,
                        new ModelSourceRunRequestParameterMapper.Options(false, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> ModelSourceRunRequestParameterMapper.apply(InferenceRequest.builder(), null));
    }

    private static InferenceRequest request(ModelSourceRunRequestParameterMapper.Options options) {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model("test-model")
                .prompt("test prompt");
        ModelSourceRunRequestParameterMapper.apply(builder, options);
        return builder.build();
    }
}
