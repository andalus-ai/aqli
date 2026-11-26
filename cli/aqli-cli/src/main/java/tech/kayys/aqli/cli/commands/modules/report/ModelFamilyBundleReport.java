package tech.kayys.aqli.cli.commands.modules.report;

import tech.kayys.aqli.cli.util.ModelFamilyBundleGate;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifest;
import tech.kayys.aqli.cli.util.PluginAvailabilityChecker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects model-family bundle manifest and gate values into the modules CLI report.
 *
 * <p>Discovery, manifest loading, availability evaluation, and gate policy remain
 * owned by their existing services. This class only shapes supplied values for output.</p>
 */
public final class ModelFamilyBundleReport {
    private ModelFamilyBundleReport() {
    }

public static Map<String, Object> modelFamilyBundleFixtureStatusReport(
            ModelFamilyBundleManifest.FixtureStatus fixtureStatus) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("known", fixtureStatus.statusKnown());
        report.put("passed", fixtureStatus.passed());
        report.put("status", fixtureStatus.statusLabel());
        report.put("summary", fixtureStatus.compactStatus());
        report.put("requiredSelectors", fixtureStatus.requiredSelectors());
        report.put("requiredFamilies", fixtureStatus.requiredFamilies());
        report.put("requiredFingerprint", fixtureStatus.requiredFingerprint());
        report.put("inventoryFingerprint", fixtureStatus.inventoryFingerprint());
        report.put("availableFamilyCount", fixtureStatus.availableFamilyCount());
        report.put("fixtureFamilyCount", fixtureStatus.fixtureFamilyCount());
        report.put("requiredFamilyCount", fixtureStatus.requiredFamilyCount());
        report.put("requiredPassedCount", fixtureStatus.requiredPassedCount());
        report.put("missingRequiredCount", fixtureStatus.missingRequiredCount());
        report.put("problemFamilyCount", fixtureStatus.problemFamilyCount());
        report.put("missingRequiredFamilies", fixtureStatus.missingRequiredFamilies());
        report.put("problemFamilies", fixtureStatus.problemFamilies());
        return report;
    }

public static Map<String, Object> modelFamilyBundleAvailabilityReport(
            PluginAvailabilityChecker.ModelFamilyBundleAvailability availability) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("present", availability.present());
        report.put("detached", availability.detached());
        report.put("healthy", availability.healthy());
        report.put("status", availability.status());
        report.put("summary", availability.compactSummary());
        report.put("selectedFamilyCount", availability.selectedFamilyCount());
        report.put("discoveredSelectedFamilyCount", availability.discoveredSelectedFamilyCount());
        report.put("missingSelectedFamilyCount", availability.missingSelectedFamilyCount());
        report.put("omittedFamilyCount", availability.omittedFamilyCount());
        report.put("policyStatus", availability.policyStatus());
        report.put("policyViolationCount", availability.policyViolationCount());
        report.put("productionSafetyStatus", availability.productionSafetyStatus());
        report.put("productionSafetyPassed", availability.productionSafetyPassed());
        report.put("productionPendingTokenizerFamilies", availability.productionPendingTokenizerFamilies());
        report.put("catalogReadinessStatus", availability.catalogReadinessStatus());
        report.put("catalogReadinessPassed", availability.catalogReadinessPassed());
        report.put("productionReadinessPendingCount", availability.productionReadinessPendingCount());
        report.put("directSafetensorPendingCount", availability.directSafetensorPendingCount());
        report.put("productionReadinessPendingFamilies", availability.productionReadinessPendingFamilies());
        report.put("directSafetensorPendingFamilies", availability.directSafetensorPendingFamilies());
        report.put("fixtureStatus", availability.fixtureStatus());
        report.put("fixturePassed", availability.fixturePassed());
        report.put("fixtureMissingRequiredCount", availability.fixtureMissingRequiredCount());
        report.put("fixtureProblemFamilyCount", availability.fixtureProblemFamilyCount());
        report.put("presetConformanceStatus", availability.presetConformanceStatus());
        report.put("problems", availability.problems());
        report.put("remediationHints", availability.remediationHints());
        report.put("missingSelectedFamilies", availability.missingSelectedFamilies());
        report.put("omittedFamilies", availability.omittedFamilies());
        report.put("fixtureMissingRequiredFamilies", availability.fixtureMissingRequiredFamilies());
        report.put("fixtureProblemFamilies", availability.fixtureProblemFamilies());
        return report;
    }

public static Map<String, Object> modelFamilyBundleProductionSafetyReport(ModelFamilyBundleManifest manifest) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tokenizerMetadataRequired", manifest.productionTokenizerMetadataRequired());
        report.put("tokenizerMetadataReady", manifest.productionTokenizerMetadataReady());
        report.put("passed", manifest.productionSafetyPassed());
        report.put("status", manifest.productionSafetyStatusLabel());
        report.put("summary", manifest.displayProductionSafetyStatus());
        report.put("pendingTokenizerFamilyCount", manifest.selectedTokenizerMetadataPendingFamilies().size());
        report.put("pendingTokenizerFamilies", manifest.selectedTokenizerMetadataPendingFamilies());
        report.put("pendingTokenizerReasons", manifest.selectedTokenizerMetadataPendingReasons());
        return report;
    }

public static Map<String, Object> modelFamilyBundleCatalogReadinessReport(ModelFamilyBundleManifest manifest) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("statusKnown", manifest.catalogReadinessStatusKnown());
        report.put("passed", manifest.catalogReadinessPassed());
        report.put("status", manifest.catalogReadinessStatusLabel());
        report.put("summary", manifest.displayCatalogReadinessStatus());
        report.put("productionReadinessPendingCount", manifest.productionReadinessPendingCount());
        report.put("directSafetensorPendingCount", manifest.directSafetensorPendingCount());
        report.put("productionReadinessPendingFamilies", manifest.productionReadinessPendingFamilies());
        report.put("directSafetensorPendingFamilies", manifest.directSafetensorPendingFamilies());
        return report;
    }

public static Map<String, Object> gate(ModelFamilyBundleGate gate, boolean failOnModelFamilyGate) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", gate.passed());
        report.put("failed", gate.failed());
        report.put("status", gate.status());
        report.put("violationCount", gate.violationCount());
        report.put("violations", gate.violations());
        report.put("contractCategoryCounts", gate.contractCategoryCounts());
        report.put("contractRemediationHints", gate.contractRemediationHints());
        report.put("failOnModelFamilyGate", failOnModelFamilyGate);
        return report;
    }

public static Map<String, Object> modelFamilyBundlePresetReport(ModelFamilyBundleManifest.BundlePreset preset) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", preset.id());
        report.put("description", preset.description());
        report.put("selectors", preset.selectors());
        report.put("requiredFamilies", preset.requiredFamilies());
        report.put("forbiddenFamilies", preset.forbiddenFamilies());
        report.put("requiredAliases", preset.requiredAliases());
        report.put("forbiddenAliases", preset.forbiddenAliases());
        report.put("selectedFamilies", preset.selectedFamilies());
        report.put("selectedCount", preset.selectedCount());
        Map<String, Object> productionSafety = new LinkedHashMap<>();
        productionSafety.put("known", preset.productionSafetyStatusKnown());
        productionSafety.put("tokenizerMetadataRequired", preset.productionTokenizerMetadataRequired());
        productionSafety.put("tokenizerMetadataReady", preset.productionTokenizerMetadataReady());
        productionSafety.put("passed", preset.productionSafetyPassed());
        productionSafety.put("status", preset.productionSafetyStatusLabel());
        productionSafety.put("summary", preset.productionSafetyCompactStatus());
        productionSafety.put("violationCount", preset.productionSafetyViolationCount());
        productionSafety.put("pendingTokenizerFamilyCount", preset.pendingTokenizerFamilies().size());
        productionSafety.put("pendingTokenizerFamilies", preset.pendingTokenizerFamilies());
        productionSafety.put("pendingTokenizerReasons", preset.pendingTokenizerReasons());
        report.put("productionSafety", productionSafety);
        Map<String, Object> policyStatus = new LinkedHashMap<>();
        policyStatus.put("known", preset.policyStatusKnown());
        policyStatus.put("passed", preset.policyPassed());
        policyStatus.put("status", preset.policyStatusLabel());
        policyStatus.put("violationCount", preset.policyViolationCount());
        report.put("policyStatus", policyStatus);
        report.put("policyViolations", modelFamilyBundlePolicyViolationsReport(preset.policyViolations()));
        return report;
    }

public static Map<String, Object> modelFamilyBundlePresetConformanceReport(
            ModelFamilyBundleManifest.BundlePresetConformance conformance) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("presetId", conformance.hasPreset() ? conformance.presetId() : null);
        report.put("presetMetadataPresent", conformance.presetMetadataPresent());
        report.put("status", conformance.statusLabel());
        report.put("summary", conformance.compactSummary());
        report.put("matchesPreset", conformance.matchesPreset());
        report.put("cleanPresetBuild", conformance.cleanPresetBuild());
        report.put("selectorsMatch", conformance.selectorsMatch());
        report.put("policyInputsMatch", conformance.policyInputsMatch());
        report.put("explicitSelectorOverride", conformance.explicitSelectorOverride());
        report.put("explicitPolicyOverride", conformance.explicitPolicyOverride());
        report.put("selectorAdditions", conformance.selectorAdditions());
        report.put("selectorOmissions", conformance.selectorOmissions());
        report.put("requiredFamilyAdditions", conformance.requiredFamilyAdditions());
        report.put("requiredFamilyOmissions", conformance.requiredFamilyOmissions());
        report.put("forbiddenFamilyAdditions", conformance.forbiddenFamilyAdditions());
        report.put("forbiddenFamilyOmissions", conformance.forbiddenFamilyOmissions());
        report.put("requiredAliasAdditions", conformance.requiredAliasAdditions());
        report.put("requiredAliasOmissions", conformance.requiredAliasOmissions());
        report.put("forbiddenAliasAdditions", conformance.forbiddenAliasAdditions());
        report.put("forbiddenAliasOmissions", conformance.forbiddenAliasOmissions());
        return report;
    }

public static Map<String, Object> modelFamilyBundlePolicyStatusReport(ModelFamilyBundleManifest.BundlePolicy policy) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("known", policy.statusKnown());
        report.put("passed", policy.passed());
        report.put("status", policy.statusLabel());
        report.put("violationCount", policy.violationCount());
        return report;
    }

public static Map<String, Object> modelFamilyBundlePolicyViolationsReport(
            ModelFamilyBundleManifest.BundlePolicyViolations violations) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("missingRequired", violations.missingRequiredFamilies());
        report.put("selectedForbidden", violations.selectedForbiddenFamilies());
        report.put("missingRequiredAliases",
                aliasFamilyMapReport(violations.missingRequiredAliases(), "missingFamilies"));
        report.put("selectedForbiddenAliases",
                aliasFamilyMapReport(violations.selectedForbiddenAliases(), "selectedFamilies"));
        return report;
    }

static List<Map<String, Object>> aliasFamilyMapReport(Map<String, List<String>> values, String familyField) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> report = new LinkedHashMap<>();
                    report.put("alias", entry.getKey());
                    report.put(familyField, entry.getValue());
                    return report;
                })
                .toList();
    }

public static Map<String, Object> modelFamilyBundleAliasReport(ModelFamilyBundleManifest.BundleAliasCoverage alias) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", alias.id());
        report.put("description", alias.description());
        report.put("families", alias.families());
        report.put("familyCount", alias.familyCount());
        report.put("selectedFamilies", alias.selectedFamilies());
        report.put("selectedCount", alias.selectedCount());
        report.put("missingFamilies", alias.missingFamilies());
        report.put("missingCount", alias.missingCount());
        report.put("complete", alias.complete());
        report.put("partial", alias.partial());
        return report;
    }
}
