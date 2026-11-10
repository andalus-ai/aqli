/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.plugin.core.ExtensionAvailability;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractValidator;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gemma4UnifiedRuntimeAvailabilityProviderTest {
    @Test
    void reportsDetachedUntilUnifiedRuntimePluginIsAttached() {
        Gemma4UnifiedRuntimeAvailabilityProvider provider = new Gemma4UnifiedRuntimeAvailabilityProvider();

        ExtensionAvailability availability = provider.availability();

        assertEquals(Gemma4UnifiedRuntimeAvailabilityProvider.ID, availability.id());
        assertEquals("Gemma 4 Unified Runtime", availability.name());
        assertEquals("multimodal-runtime", availability.kind());
        assertFalse(availability.attached());
        assertTrue(availability.detached());
        assertTrue(availability.healthy());
        assertFalse(availability.productionReady());
        assertEquals("detached", availability.status());
        assertEquals("gemma4", availability.attributes().get("modelFamilyId"));
        assertEquals("gemma4_unified", availability.attributes().get("modelType"));
        assertEquals("google/gemma-4-12B-it", availability.attributes().get("checkpoint"));
        assertTrue(availability.capabilities().contains("unified_multimodal_embedding"));
        assertTrue(availability.diagnostics().contains("UnifiedMultimodalRuntime"));
        assertTrue(availability.remediationHints().stream()
                .anyMatch(hint -> hint.contains("gemma4_unified")));
        assertTrue(ExtensionAvailabilityContractValidator.validate(provider, availability).isEmpty());
    }

    @Test
    void providerIsDiscoverableThroughPluginCoreServiceLoader() {
        ExtensionAvailabilityRegistry registry = new ExtensionAvailabilityRegistry();

        registry.discoverServiceLoaderProviders();

        assertTrue(registry.providers().stream()
                .anyMatch(provider -> Gemma4UnifiedRuntimeAvailabilityProvider.ID.equals(provider.extensionId())));
        ExtensionAvailability availability = registry.availability(Gemma4UnifiedRuntimeAvailabilityProvider.ID)
                .orElseThrow();
        assertEquals("multimodal-runtime", availability.kind());
        assertEquals("gemma4_unified", availability.attributes().get("modelType"));
    }
}
