package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SafetensorEngineHintPolicyTest {

    @Test
    void mapsJavaHintsToDirectExecution() {
        for (String hint : new String[] {"java", "java-native", "native", "direct"}) {
            assertEquals("direct", SafetensorEngineHintPolicy.backendFor(hint));
        }
    }

    @Test
    void mapsLlamaCppHintsToGgufConversionRoute() {
        for (String hint : new String[] {"llamacpp", "llama.cpp", "llama-cpp", "gguf"}) {
            assertEquals("gguf", SafetensorEngineHintPolicy.backendFor(hint));
        }
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("direct", SafetensorEngineHintPolicy.backendFor("  JAVA-NATIVE "));
        assertEquals("gguf", SafetensorEngineHintPolicy.backendFor(" LLAMA.CPP "));
    }

    @Test
    void leavesAutomaticBenchmarkAndUnknownHintsUnmapped() {
        for (String hint : new String[] {null, "", "auto", "benchmark", "metal", "unknown"}) {
            assertNull(SafetensorEngineHintPolicy.backendFor(hint));
        }
    }
}
