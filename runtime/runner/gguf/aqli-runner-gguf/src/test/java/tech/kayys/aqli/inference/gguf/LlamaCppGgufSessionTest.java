package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlamaCppGgufSessionTest {

    @Test
    void sizesOutputBufferFromRequestedTokenCountWithMinimumCapacity() {
        assertEquals(64L * 1024L, LlamaCppGgufSession.outputBufferBytesForTokens(0));
        assertEquals(64L * 1024L, LlamaCppGgufSession.outputBufferBytesForTokens(32));
        assertEquals(512L * 1024L, LlamaCppGgufSession.outputBufferBytesForTokens(1024));
    }

    @Test
    void doublesOutputBufferWithoutOverflow() {
        assertEquals(128L * 1024L, LlamaCppGgufSession.nextOutputBufferBytes(64L * 1024L));
        assertEquals(Long.MAX_VALUE, LlamaCppGgufSession.nextOutputBufferBytes(Long.MAX_VALUE));
    }

    @Test
    void recognizesNativeAndDiagnosticOutputBufferFailures() {
        assertTrue(LlamaCppGgufSession.outputBufferTooSmall(-9, null));
        assertTrue(LlamaCppGgufSession.outputBufferTooSmall(-1, "Output buffer too small"));
        assertFalse(LlamaCppGgufSession.outputBufferTooSmall(-1, "model load failed"));
    }
}
