package tech.kayys.aqli.cli.commands.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.cli.commands.audio.AudioRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.image.ImageRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration;
import tech.kayys.aqli.cli.commands.text.TextRunRequestParameterMapper;
import tech.kayys.aqli.spi.inference.InferenceRequest;

class RunInferenceRequestAssemblerTest {

    @Test
    void assemblesBaseCapabilityRuntimeAndConversationInputs() {
        InferenceRequest request = RunInferenceRequestAssembler.assemble(
                new RunInferenceRequestAssembler.Options(
                        new RunRequestBaseAssembler.Options(
                                "model-id",
                                "user prompt",
                                "plugin-id",
                                true,
                                true,
                                "/models/model",
                                "hf:org/model"),
                        new TextRunRequestParameterMapper.Options(
                                0.4, 0.8, 32, 1.05, true, 128, 2, "root ::= \\"ok\\""),
                        new RunInferenceRequestAssembler.QuantizationOptions("awq", 4, "q8"),
                        true,
                        new RuntimeRoutingRunRequestConfiguration.Options(
                                Map.of("route", "test"),
                                true,
                                "safetensor",
                                "java",
                                "chat",
                                "native",
                                false),
                        new ImageRunRequestParameterMapper.Options(
                                7L, 20, "png", 7.5f, "out.png", 512, 512,
                                List.of(), false, null, false, null, false, false),
                        new AudioRunRequestParameterMapper.Options(
                                null, null, null, null, null, null, null, null, null,
                                false, false, false, null, null, null, false, null, null,
                                null, null, false, false, false, false, null),
                        "json",
                        List.of(),
                        null,
                        new RagRunRequestConfiguration.RagContext(
                                "retrieved context", List.of("inline"), "full_context"),
                        "embedding-model",
                        new RunInferenceRequestAssembler.ConversationOptions(
                                "session-1", "system prompt", "retrieval system prompt", "user prompt"),
                        true));

        assertEquals("model-id", request.getModel());
        assertEquals("plugin-id", request.getPlugin().orElseThrow());
        assertTrue(request.isStreaming());
        assertEquals("session-1", request.getSessionId().orElseThrow());
        assertEquals(3, request.getMessages().size());
        assertEquals("system prompt", request.getMessages().get(0).getContent());
        assertEquals("retrieval system prompt", request.getMessages().get(1).getContent());
        assertEquals("user prompt", request.getMessages().get(2).getContent());

        assertEquals(0.4, request.getTemperature());
        assertEquals(128, request.getMaxTokens());
        assertEquals("awq", request.getParameters().get("quantization_strategy"));
        assertEquals(4, request.getParameters().get("quantization_bits"));
        assertEquals(true, request.getParameters().get("profile"));
        assertEquals("java", request.getParameters().get("gguf.backend"));
        assertEquals("retrieved context", request.getParameters().get("rag_context"));
        assertEquals("embedding-model", request.getParameters().get("embedding_model"));
        assertEquals("json", request.getParameters().get("output_format"));
        assertTrue(request.isCacheBypass());
        assertEquals("test", request.getMetadata().get("route"));
    }
}
