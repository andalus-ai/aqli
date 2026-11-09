package tech.kayys.aqli.inference.llamacpp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LlamaCppEngineProviderIdentityTest {
    @Test
    void identifiesExecutionProviderRatherThanModelFormat() {
        LlamaCppEngine engine = new LlamaCppEngine(null, null, null, null, null);
        assertEquals("llamacpp", engine.providerId());
    }
}
