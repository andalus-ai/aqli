package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.cli.commands.route.SafetensorEngineHintPolicy;

/** --java / --llamacpp must map onto the SafeTensor engine's backend ids (direct / gguf). */
class RunCommandSafetensorEngineHintTest {

    @Test
    void javaAliasesSelectDirectBackend() {
        assertEquals("direct", SafetensorEngineHintPolicy.backendFor("java"));
        assertEquals("direct", SafetensorEngineHintPolicy.backendFor(" Java-Native "));
        assertEquals("direct", SafetensorEngineHintPolicy.backendFor("direct"));
    }

    @Test
    void llamaCppAliasesSelectGgufBackend() {
        assertEquals("gguf", SafetensorEngineHintPolicy.backendFor("llamacpp"));
        assertEquals("gguf", SafetensorEngineHintPolicy.backendFor("llama.cpp"));
        assertEquals("gguf", SafetensorEngineHintPolicy.backendFor("gguf"));
    }

    @Test
    void autoBenchmarkAndUnknownKeepConfiguredBackend() {
        assertNull(SafetensorEngineHintPolicy.backendFor(null));
        assertNull(SafetensorEngineHintPolicy.backendFor("auto"));
        assertNull(SafetensorEngineHintPolicy.backendFor("benchmark"));
        assertNull(SafetensorEngineHintPolicy.backendFor("nonsense"));
    }
}
