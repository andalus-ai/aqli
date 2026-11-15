package tech.kayys.aqli.cli.commands.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class RuntimeExecutionRunRequestParameterMapperTest {

    @Test
    void mapsQuantizationProfilingAndRoutingOptions() {
        InferenceRequest request = request(new RuntimeExecutionRunRequestParameterMapper.Options(
                "grouped", 4, "q8_0", true,
                Map.of("route.source", "cli"), "java", "java", "java"));

        assertEquals("grouped", request.getParameters().get("quantize_strategy"));
        assertEquals(4, request.getParameters().get("quantize_bits"));
        assertEquals("q8_0", request.getParameters().get("kv_cache_quant"));
        assertEquals(true, request.getParameters().get("profile"));
        assertEquals(true, request.getParameters().get("onnx_profile"));
        assertEquals("java", request.getParameters().get("runner"));
        assertEquals("java", request.getParameters().get("gguf.backend"));
        assertEquals("cli", request.getMetadata().get("route.source"));
        assertEquals(true, request.getMetadata().get("profile"));
        assertEquals(true, request.getMetadata().get("onnx_profile"));
        assertEquals("java", request.getMetadata().get("runner"));
        assertEquals("java", request.getMetadata().get("gguf.backend"));
    }

    @Test
    void omitsUnsetOptionalRuntimeOptions() {
        InferenceRequest request = request(new RuntimeExecutionRunRequestParameterMapper.Options(
                null, null, "none", false, null, null, null, null));

        assertEquals(Map.of(), request.getParameters());
        assertEquals(Map.of(), request.getMetadata());
    }

    @Test
    void doesNotSetRunnerWhenOnlyBlankRunnerIsProvided() {
        InferenceRequest request = request(new RuntimeExecutionRunRequestParameterMapper.Options(
                null, null, null, false, null, " ", null, null));

        assertFalse(request.getParameters().containsKey("runner"));
        assertFalse(request.getMetadata().containsKey("runner"));
    }

    @Test
    void rejectsMissingRequestOrOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeExecutionRunRequestParameterMapper.apply(null,
                        new RuntimeExecutionRunRequestParameterMapper.Options(
                                null, null, null, false, null, null, null, null)));
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
