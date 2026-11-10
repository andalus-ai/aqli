/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.CatalogReadiness;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.FixtureStatus;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.Manifest;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.PolicyStatus;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.PresetConformance;
import tech.kayys.aqli.cli.util.ModelFamilyBundleManifestReportFields.ProductionSafety;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the reusable model-family bundle manifest section for CI reports.
 */
final class ModelFamilyBundleManifestReports {
    private ModelFamilyBundleManifestReports() {
    }

    static Map<String, Object> manifest(ModelFamilyBundleManifest manifest) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Manifest.PRESENT, manifest.present());
        if (!manifest.present()) {
            return report;
        }

        report.put(Manifest.SCHEMA_VERSION, manifest.schemaVersion());
        report.put(Manifest.FINGERPRINT, manifest.bundleFingerprint());
        report.put(Manifest.DETACHED, manifest.detached());
        report.put(Manifest.COUNT_CONSISTENCY_PROBLEMS, manifest.countConsistencyProblems());
        report.put(Manifest.BUNDLE_PRESET, manifest.hasBundlePreset() ? manifest.bundlePreset() : null);
        report.put(Manifest.REQUIRES_DIRECT_SAFETENSOR_RUNTIME, manifest.requiresDirectSafetensorRuntime());
        report.put(Manifest.PRODUCTION_SAFETY, productionSafety(manifest));
        report.put(Manifest.CATALOG_READINESS, catalogReadiness(manifest));
        report.put(Manifest.SELECTOR_SOURCE, manifest.selectorSource());
        report.put(Manifest.POLICY_SOURCE, manifest.policySource());
        report.put(Manifest.SELECTORS, manifest.selectors());
        report.put(Manifest.FAMILIES, manifest.families());
        report.put(Manifest.PROFILES, manifest.profiles());
        report.put(Manifest.REQUIRED_FAMILIES, manifest.bundlePolicy().requiredFamilies());
        report.put(Manifest.FORBIDDEN_FAMILIES, manifest.bundlePolicy().forbiddenFamilies());
        report.put(Manifest.REQUIRED_ALIASES, manifest.bundlePolicy().requiredAliases());
        report.put(Manifest.FORBIDDEN_ALIASES, manifest.bundlePolicy().forbiddenAliases());
        report.put(Manifest.POLICY_STATUS, bundlePolicyStatus(manifest.bundlePolicy()));
        report.put(Manifest.FIXTURE_STATUS, fixtureStatus(manifest.fixtureStatus()));
        report.put(Manifest.ACTIVE_BUNDLE_PRESET_CONFORMANCE,
                presetConformance(manifest.activeBundlePresetConformance()));
        return report;
    }

    private static Map<String, Object> productionSafety(ModelFamilyBundleManifest manifest) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(ProductionSafety.TOKENIZER_METADATA_REQUIRED, manifest.productionTokenizerMetadataRequired());
        report.put(ProductionSafety.TOKENIZER_METADATA_READY, manifest.productionTokenizerMetadataReady());
        report.put(ProductionSafety.PASSED, manifest.productionSafetyPassed());
        report.put(ProductionSafety.STATUS, manifest.productionSafetyStatusLabel());
        report.put(ProductionSafety.SUMMARY, manifest.displayProductionSafetyStatus());
        report.put(ProductionSafety.PENDING_TOKENIZER_FAMILIES, manifest.selectedTokenizerMetadataPendingFamilies());
        report.put(ProductionSafety.PENDING_TOKENIZER_REASONS, manifest.selectedTokenizerMetadataPendingReasons());
        return report;
    }

    private static Map<String, Object> catalogReadiness(ModelFamilyBundleManifest manifest) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(CatalogReadiness.STATUS_KNOWN, manifest.catalogReadinessStatusKnown());
        report.put(CatalogReadiness.PASSED, manifest.catalogReadinessPassed());
        report.put(CatalogReadiness.STATUS, manifest.catalogReadinessStatusLabel());
        report.put(CatalogReadiness.SUMMARY, manifest.displayCatalogReadinessStatus());
        report.put(CatalogReadiness.PRODUCTION_READINESS_PENDING_COUNT,
                manifest.productionReadinessPendingCount());
        report.put(CatalogReadiness.DIRECT_SAFETENSOR_PENDING_COUNT, manifest.directSafetensorPendingCount());
        report.put(CatalogReadiness.PRODUCTION_READINESS_PENDING_FAMILIES,
                manifest.productionReadinessPendingFamilies());
        report.put(CatalogReadiness.DIRECT_SAFETENSOR_PENDING_FAMILIES,
                manifest.directSafetensorPendingFamilies());
        return report;
    }

    private static Map<String, Object> bundlePolicyStatus(ModelFamilyBundleManifest.BundlePolicy policy) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(PolicyStatus.KNOWN, policy.statusKnown());
        report.put(PolicyStatus.PASSED, policy.passed());
        report.put(PolicyStatus.STATUS, policy.statusLabel());
        report.put(PolicyStatus.VIOLATION_COUNT, policy.violationCount());
        return report;
    }

    private static Map<String, Object> fixtureStatus(ModelFamilyBundleManifest.FixtureStatus fixtureStatus) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(FixtureStatus.KNOWN, fixtureStatus.statusKnown());
        report.put(FixtureStatus.PASSED, fixtureStatus.passed());
        report.put(FixtureStatus.STATUS, fixtureStatus.statusLabel());
        report.put(FixtureStatus.SUMMARY, fixtureStatus.compactStatus());
        report.put(FixtureStatus.REQUIRED_SELECTORS, fixtureStatus.requiredSelectors());
        report.put(FixtureStatus.REQUIRED_FAMILIES, fixtureStatus.requiredFamilies());
        report.put(FixtureStatus.REQUIRED_FINGERPRINT, fixtureStatus.requiredFingerprint());
        report.put(FixtureStatus.INVENTORY_FINGERPRINT, fixtureStatus.inventoryFingerprint());
        report.put(FixtureStatus.AVAILABLE_FAMILY_COUNT, fixtureStatus.availableFamilyCount());
        report.put(FixtureStatus.FIXTURE_FAMILY_COUNT, fixtureStatus.fixtureFamilyCount());
        report.put(FixtureStatus.REQUIRED_FAMILY_COUNT, fixtureStatus.requiredFamilyCount());
        report.put(FixtureStatus.REQUIRED_PASSED_COUNT, fixtureStatus.requiredPassedCount());
        report.put(FixtureStatus.MISSING_REQUIRED_COUNT, fixtureStatus.missingRequiredCount());
        report.put(FixtureStatus.PROBLEM_FAMILY_COUNT, fixtureStatus.problemFamilyCount());
        report.put(FixtureStatus.MISSING_REQUIRED_FAMILIES, fixtureStatus.missingRequiredFamilies());
        report.put(FixtureStatus.PROBLEM_FAMILIES, fixtureStatus.problemFamilies());
        return report;
    }

    private static Map<String, Object> presetConformance(
            ModelFamilyBundleManifest.BundlePresetConformance conformance) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(PresetConformance.PRESET_ID, conformance.hasPreset() ? conformance.presetId() : null);
        report.put(PresetConformance.PRESET_METADATA_PRESENT, conformance.presetMetadataPresent());
        report.put(PresetConformance.STATUS, conformance.statusLabel());
        report.put(PresetConformance.SUMMARY, conformance.compactSummary());
        report.put(PresetConformance.MATCHES_PRESET, conformance.matchesPreset());
        report.put(PresetConformance.CLEAN_PRESET_BUILD, conformance.cleanPresetBuild());
        report.put(PresetConformance.SELECTORS_MATCH, conformance.selectorsMatch());
        report.put(PresetConformance.POLICY_INPUTS_MATCH, conformance.policyInputsMatch());
        report.put(PresetConformance.EXPLICIT_SELECTOR_OVERRIDE, conformance.explicitSelectorOverride());
        report.put(PresetConformance.EXPLICIT_POLICY_OVERRIDE, conformance.explicitPolicyOverride());
        return report;
    }
}
