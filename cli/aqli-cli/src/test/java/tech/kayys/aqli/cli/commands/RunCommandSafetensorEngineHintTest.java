package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** --java / --llamacpp must map onto the SafeTensor engine's backend ids (direct / gguf). */
class RunCommandSafetensorEngineHintTest {

    @Test
    void javaAliasesSelectDirectBackend() {
        assertEquals("direct", RunCommand.safetensorBackendFor("java"));
        assertEquals("direct", RunCommand.safetensorBackendFor(" Java-Native "));
        assertEquals("direct", RunCommand.safetensorBackendFor("direct"));
    }

    @Test
    void llamaCppAliasesSelectGgufBackend() {
        assertEquals("gguf", RunCommand.safetensorBackendFor("llamacpp"));
        assertEquals("gguf", RunCommand.safetensorBackendFor("llama.cpp"));
        assertEquals("gguf", RunCommand.safetensorBackendFor("gguf"));
    }

    @Test
    void autoBenchmarkAndUnknownKeepConfiguredBackend() {
        assertNull(RunCommand.safetensorBackendFor(null));
        assertNull(RunCommand.safetensorBackendFor("auto"));
        assertNull(RunCommand.safetensorBackendFor("benchmark"));
        assertNull(RunCommand.safetensorBackendFor("nonsense"));
    }
}
