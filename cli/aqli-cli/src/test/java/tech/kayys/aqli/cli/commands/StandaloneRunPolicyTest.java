package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneRunPolicyTest {

    @Test
    void litertRequiresSimplePromptRunAndCompatibleProvider() {
        assertTrue(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, "hello", null, true, true));
        assertTrue(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, "hello", "litertlm", true, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(true, true, "hello", null, true, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, false, "hello", null, true, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, " ", null, true, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, "hello", "onnx", true, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, "hello", null, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseLiteRtStandalone(false, true, "hello", null, true, false));
    }

    @Test
    void cachedGemma4AlternateRuntimeRequiresModelReferenceAndNoConflictingPath() {
        assertTrue(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, "gemma4", null, null, null, null, false, true));
        assertTrue(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, "gemma4", "", "", "", "litert", true, true));
        assertFalse(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                false, "gemma4", null, null, null, null, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, null, null, null, null, null, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, "gemma4", "model.safetensors", null, null, null, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, "gemma4", null, null, null, "onnx", false, true));
        assertFalse(StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                true, "gemma4", null, null, null, "litert", false, false));
    }

    @Test
    void ggufEligibilityHonorsExplicitProviderAndModelSignals() {
        assertTrue(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, "hello", null, false, false, false, false, false,
                false, false, false, false, true));
        assertTrue(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, "hello", "gguf", true, false, false, false, false,
                false, false, false, false, false));
        assertTrue(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, "hello", null, false, false, true, true, true,
                false, false, false, false, false));
        assertFalse(StandaloneRunPolicy.shouldUseGgufStandalone(
                true, "hello", null, false, false, false, false, false,
                false, false, false, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, " ", null, false, false, false, false, false,
                false, false, false, false, true));
        assertFalse(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, "hello", "onnx", false, true, false, false, false,
                true, true, true, true, true));
        assertFalse(StandaloneRunPolicy.shouldUseGgufStandalone(
                false, "hello", null, false, false, true, true, false,
                false, false, false, false, false));
    }
}
