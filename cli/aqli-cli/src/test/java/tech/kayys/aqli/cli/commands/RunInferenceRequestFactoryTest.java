package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import tech.kayys.aqli.spi.inference.InferenceRequest;

class RunInferenceRequestFactoryTest {

    @Test
    void createsCommonRequestFieldsAndPreservesCliDefaults() {
        InferenceRequest request = RunInferenceRequestFactory.createBase(
                new RunInferenceRequestFactory.Options(
                        "model.gguf",
                        "hello",
                        0.2,
                        0.9,
                        40,
                        1.1,
                        false,
                        2048,
                        "native",
                        true,
                        " session-1 ",
                        2,
                        "root ::= \"hello\"",
                        true))
                .build();

        assertNotNull(request.getRequestId());
        assertEquals("model.gguf", request.getModel());
        assertEquals("hello", request.getPrompt());
        assertEquals(Optional.of("native"), request.getPlugin());
        assertEquals(Optional.of("session-1"), request.getSessionId());
        assertEquals(2, request.getMirostat());
        assertEquals("root ::= \"hello\"", request.getGrammar());
        assertTrue(request.isCacheBypass());
        assertTrue(request.isStreaming());
        assertEquals(0.2, request.getParameters().get("temperature"));
        assertEquals(0.9, request.getParameters().get("top_p"));
        assertEquals(40, request.getParameters().get("top_k"));
        assertEquals(1.1, request.getParameters().get("repeat_penalty"));
        assertEquals(2048, request.getParameters().get("max_tokens"));
    }

    @Test
    void omitsOptionalSessionMirostatAndGrammarWhenNotConfigured() {
        InferenceRequest request = RunInferenceRequestFactory.createBase(
                new RunInferenceRequestFactory.Options(
                        "model",
                        "prompt",
                        0.2,
                        0.9,
                        40,
                        1.1,
                        false,
                        128,
                        null,
                        false,
                        " ",
                        0,
                        "",
                        false))
                .build();

        assertFalse(request.isStreaming());
        assertEquals("model", request.getModel());
        assertNotNull(request.getRequestId());
    }
}
