package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GgufRunArgumentsTest {

    @Test
    void parsesRunOptionsAndClampsSamplingValues() {
        GgufRunArguments arguments = GgufRunArguments.parse(
                new String[] {
                    "run", "--model=hf:example/model", "--prompt", "hello",
                    "--max-tokens", "0", "--temperature", "-1",
                    "--top-p", "1.5", "--top-k", "-4",
                    "--repeat-penalty", "1.1", "--java", "--use-cpu", "--only-text"
                },
                "metal",
                "auto");

        assertEquals("hf:example/model", arguments.model());
        assertEquals("hello", arguments.prompt());
        assertEquals(1, arguments.maxTokens());
        assertEquals(0.0d, arguments.temperature());
        assertEquals(1.0d, arguments.topP());
        assertEquals(1, arguments.topK());
        assertEquals(1.1d, arguments.repeatPenalty());
        assertEquals("java", arguments.engine());
        assertEquals("cpu", arguments.backend());
        assertFalse(arguments.banner());
        assertTrue(arguments.noInfo());
        assertTrue(arguments.supported());
    }

    @Test
    void appliesCallerSuppliedDefaultsWithoutReadingProcessConfiguration() {
        GgufRunArguments arguments = GgufRunArguments.parse(
                new String[] {"run", "--model", "model.gguf", "--prompt", "hello"},
                "cpu",
                "java");

        assertEquals("cpu", arguments.backend());
        assertEquals("java", arguments.engine());
        assertEquals(2048, arguments.maxTokens());
        assertEquals(0.2d, arguments.temperature());
        assertEquals(0.9d, arguments.topP());
        assertEquals(40, arguments.topK());
    }

    @Test
    void unsupportedOptionsAndCommandsAreMarkedForFallback() {
        GgufRunArguments unknownOption = GgufRunArguments.parse(
                new String[] {"run", "--model", "model.gguf", "--prompt", "hello", "--unknown"},
                "metal",
                "auto");
        GgufRunArguments wrongCommand = GgufRunArguments.parse(
                new String[] {"prewarm", "--model", "model.gguf", "--prompt", "hello"},
                "metal",
                "auto");

        assertFalse(unknownOption.optionParsingSupported());
        assertFalse(unknownOption.supported());
        assertFalse(wrongCommand.optionParsingSupported());
        assertFalse(wrongCommand.supported());
    }

    @Test
    void identifiesNonGgufProvidersWithoutTreatingThemAsSupported() {
        GgufRunArguments arguments = GgufRunArguments.parse(
                new String[] {"run", "--model", "model.gguf", "--prompt", "hello", "--provider", "openai"},
                "metal",
                "auto");

        assertFalse(arguments.supported());
        assertTrue(arguments.hasNonGgufProvider());
    }
}
