package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFamilyBundleDiagnosticsRendererTest {
    @Test
    void rendersBundleDiagnosticsWithoutCheckerDiscovery() {
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("bundleFingerprint", "sha256:renderer");
        properties.setProperty("selectors", "direct,vlm,mystery");
        properties.setProperty("selectorSource", "preset");
        properties.setProperty("policySource", "mixed");
        properties.setProperty("bundlePreset", "prod_llm");
        properties.setProperty("families", "gemma,kimi");
        properties.setProperty("availableFamilies", "bert,flava,gemma,kimi");
        properties.setProperty("profiles", "core,optional");
        properties.setProperty("family.bert.profile", "metadata_only");
        properties.setProperty("family.flava.profile", "metadata_only");
        properties.setProperty("tokenizerMetadataPendingFamilies", "kimi");
        properties.setProperty("family.kimi.tokenizerMetadataPendingReason", "adapter pending");
        properties.setProperty("productionReadinessPassed", "false");
        properties.setProperty("productionReadinessPendingCount", "1");
        properties.setProperty("productionReadinessPendingFamilies", "kimi");
        properties.setProperty("directSafetensorReadinessPassed", "true");
        properties.setProperty("directSafetensorPendingCount", "0");
        properties.setProperty("policyPassed", "false");
        properties.setProperty("policyViolationCount", "1");
        properties.setProperty("missingRequiredFamilies", "qwen");
        properties.setProperty("fixturePassed", "true");
        properties.setProperty("fixtureRequiredFamilies", "gemma,kimi");
        properties.setProperty("fixtureRequiredFamilyCount", "2");
        properties.setProperty("fixtureRequiredPassedCount", "2");
        properties.setProperty("bundleAliases", "direct,vlm");
        properties.setProperty("bundleAlias.direct.families", "gemma,kimi");
        properties.setProperty("bundleAlias.direct.familyCount", "2");
        properties.setProperty("bundleAlias.vlm.families", "gemma,flava");
        properties.setProperty("bundleAlias.vlm.familyCount", "2");
        properties.setProperty("bundlePresets", "prod_llm");
        properties.setProperty("bundlePreset.prod_llm.description", "Lean production LLM");
        properties.setProperty("bundlePreset.prod_llm.selectors", "direct");
        properties.setProperty("bundlePreset.prod_llm.requiredAliases", "direct");
        properties.setProperty("bundlePreset.prod_llm.selectedFamilies", "gemma,kimi");
        properties.setProperty("bundlePreset.prod_llm.selectedCount", "2");
        properties.setProperty("bundlePreset.prod_llm.productionTokenizerMetadataRequired", "true");
        properties.setProperty("bundlePreset.prod_llm.productionTokenizerMetadataReady", "false");
        properties.setProperty("bundlePreset.prod_llm.productionSafetyPassed", "false");
        properties.setProperty("bundlePreset.prod_llm.productionSafetyViolationCount", "1");
        properties.setProperty("bundlePreset.prod_llm.pendingTokenizerFamilies", "kimi");
        properties.setProperty("bundlePreset.prod_llm.pendingTokenizerFamily.kimi.reason", "adapter pending");
        properties.setProperty("bundlePreset.prod_llm.policyPassed", "false");
        properties.setProperty("bundlePreset.prod_llm.policyViolationCount", "1");
        properties.setProperty("bundlePreset.prod_llm.missingRequiredFamilies", "qwen");

        String diagnostics = ModelFamilyBundleDiagnosticsRenderer.render(
                ModelFamilyBundleManifest.fromProperties(properties),
                Set.of("gemma"));

        assertTrue(diagnostics.contains("Packaged model-family bundle: selectors=direct, vlm, mystery"));
        assertTrue(diagnostics.contains(
                "Packaged model-family production safety: failed (1 pending tokenizer family(s): kimi)"));
        assertTrue(diagnostics.contains(
                "Packaged model-family catalog readiness: failed (1 production pending, 0 direct SafeTensor pending)"));
        assertTrue(diagnostics.contains("selected model-family plugins were not discovered: kimi"));
        assertTrue(diagnostics.contains("Active model-family bundle preset production safety: "
                + "failed (required, 1 pending tokenizer family(s))"));
        assertTrue(diagnostics.contains("Active preset pending tokenizer families: kimi (adapter pending)"));
        assertTrue(diagnostics.contains("Requested model-family selector aliases: direct, vlm"));
        assertTrue(diagnostics.contains("Unknown packaged selector metadata: mystery"));
        assertTrue(diagnostics.contains("Complete model-family selector aliases: direct(2/2 complete)"));
        assertTrue(diagnostics.contains("Partial model-family selector aliases: vlm(1/2 partial)"));
        assertTrue(diagnostics.contains("Unbundled model-family plugins: bert[metadata_only], flava[metadata_only]"));
        assertTrue(diagnostics.contains("Bundled model-family plugins not discovered: kimi"));
    }
}
