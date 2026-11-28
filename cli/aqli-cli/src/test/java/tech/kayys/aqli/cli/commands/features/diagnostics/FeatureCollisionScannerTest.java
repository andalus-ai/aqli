package tech.kayys.aqli.cli.commands.features.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureCollisionScannerTest {
    @Test
    void reportsCrossJarCollisionsInStableOrder() {
        var first = new FeatureCollisionScanner.JarClaims(
                "/extensions/a.jar",
                "shared-extension",
                List.of("example.Pipeline"),
                List.of("example.Adapter"),
                List.of("example.ModelFamily"),
                List.of(
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-feature", "pipeline", "example.Implementation", "shared-provider",
                                "capabilities[0]:pipeline"),
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-adapter", "adapter", "example.AdapterImpl", "shared-adapter-provider",
                                "capabilities[1]:adapter"),
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-model-family", "model-family", "example.ModelFamilyImpl", "shared-model-provider",
                                "capabilities[2]:model-family")));
        var second = new FeatureCollisionScanner.JarClaims(
                "/extensions/b.jar",
                "shared-extension",
                List.of("example.Pipeline"),
                List.of("example.Adapter"),
                List.of("example.ModelFamily"),
                List.of(
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-feature", "pipeline", "example.Implementation", "shared-provider",
                                "capabilities[0]:pipeline"),
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-adapter", "adapter", "example.AdapterImpl", "shared-adapter-provider",
                                "capabilities[1]:adapter"),
                        new FeatureCollisionScanner.CapabilityClaim(
                                "shared-model-family", "model-family", "example.ModelFamilyImpl", "shared-model-provider",
                                "capabilities[2]:model-family")));

        var report = FeatureCollisionScanner.scan(
                List.of(first, second), "pipeline-service", "adapter-service", "model-family-service");

        assertEquals(
                List.of("adapter_id", "adapter_provider_class", "extension_id", "feature_id",
                        "implementation_class", "model_family_id", "model_family_provider_class",
                        "pipeline_id", "provider_class", "provider_id"),
                report.items().stream().map(FeatureCollisionScanner.Item::kind).toList());
        assertEquals("warning", report.toMap().get("status"));
    }

    @Test
    void ignoresBlankClaimsAndDuplicatesWithinOneJar() {
        var jar = new FeatureCollisionScanner.JarClaims(
                "/extensions/a.jar",
                "same",
                List.of("example.Pipeline", "example.Pipeline"),
                List.of(),
                List.of(),
                List.of(new FeatureCollisionScanner.CapabilityClaim(
                        "feature", "pipeline", "", "", "capabilities[0]:pipeline")));

        var report = FeatureCollisionScanner.scan(
                List.of(jar), "pipeline-service", "adapter-service", "model-family-service");

        assertTrue(report.items().isEmpty());
        assertEquals("ok", report.toMap().get("status"));
    }
}
