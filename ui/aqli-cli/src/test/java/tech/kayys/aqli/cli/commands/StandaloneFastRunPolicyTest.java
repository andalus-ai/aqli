package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneFastRunPolicyTest {

    @Test
    void litertRequiresSimplePromptRunAndCompatibleProvider() {
        assertTrue(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, "hello", null, true, true));
        assertTrue(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, "hello", "litertlm", true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(true, true, "hello", null, true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(false, false, "hello", null, true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, " ", null, true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, "hello", "onnx", true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, "hello", null, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryLiteRt(false, true, "hello", null, true, false));
    }

    @Test
    void cachedGemma4AlternateRuntimeRequiresModelReferenceAndNoConflictingPath() {
        assertTrue(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, "gemma4", null, null, null, null, false, true));
        assertTrue(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, "gemma4", "", "", "", "litert", true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                false, "gemma4", null, null, null, null, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, null, null, null, null, null, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, "gemma4", "model.safetensors", null, null, null, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, "gemma4", null, null, null, "onnx", false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryCachedGemma4LiteRt(
                true, "gemma4", null, null, null, "litert", false, false));
    }

    @Test
    void ggufEligibilityHonorsExplicitProviderAndModelSignals() {
        assertTrue(StandaloneFastRunPolicy.shouldTryGguf(
                false, "hello", null, false, false, false, false, false,
                false, false, false, false, true));
        assertTrue(StandaloneFastRunPolicy.shouldTryGguf(
                false, "hello", "gguf", true, false, false, false, false,
                false, false, false, false, false));
        assertTrue(StandaloneFastRunPolicy.shouldTryGguf(
                false, "hello", null, false, false, true, true, true,
                false, false, false, false, false));
        assertFalse(StandaloneFastRunPolicy.shouldTryGguf(
                true, "hello", null, false, false, false, false, false,
                false, false, false, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryGguf(
                false, " ", null, false, false, false, false, false,
                false, false, false, false, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryGguf(
                false, "hello", "onnx", false, true, false, false, false,
                true, true, true, true, true));
        assertFalse(StandaloneFastRunPolicy.shouldTryGguf(
                false, "hello", null, false, false, true, true, false,
                false, false, false, false, false));
    }
}
