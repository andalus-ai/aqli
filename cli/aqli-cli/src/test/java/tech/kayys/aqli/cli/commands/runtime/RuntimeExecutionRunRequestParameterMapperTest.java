package tech.kayys.aqli.cli.commands.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class RuntimeExecutionRunRequestParameterMapperTest {

    @Test
    void mapsQuantizationAndProfilingOptions() {
        InferenceRequest request = request(new RuntimeExecutionRunRequestParameterMapper.Options(
                "grouped", 4, "q8_0", true));

        assertEquals("grouped", request.getParameters().get("quantize_strategy"));
        assertEquals(4, request.getParameters().get("quantize_bits"));
        assertEquals("q8_0", request.getParameters().get("kv_cache_quant"));
        assertEquals(true, request.getParameters().get("profile"));
        assertEquals(true, request.getParameters().get("onnx_profile"));
        assertEquals(true, request.getMetadata().get("profile"));
        assertEquals(true, request.getMetadata().get("onnx_profile"));
    }

    @Test
    void omitsUnsetOptionalRuntimeOptions() {
        InferenceRequest request = request(new RuntimeExecutionRunRequestParameterMapper.Options(
                null, null, "none", false));

        assertEquals(Map.of(), request.getParameters());
        assertEquals(Map.of(), request.getMetadata());
    }

    @Test
    void rejectsMissingRequestOrOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeExecutionRunRequestParameterMapper.apply(null,
                        new RuntimeExecutionRunRequestParameterMapper.Options(null, null, null, false)));
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeExecutionRunRequestParameterMapper.apply(InferenceRequest.builder(), null));
    }

    private static InferenceRequest request(RuntimeExecutionRunRequestParameterMapper.Options options) {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model("test-model")
                .prompt("test prompt");
        RuntimeExecutionRunRequestParameterMapper.apply(builder, options);
        return builder.build();
    }
}
