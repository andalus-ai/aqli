/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.List;

/**
 * Stable JSON field names for model-family bundle manifest sections in CI reports.
 */
public final class ModelFamilyBundleManifestReportFields {
    private ModelFamilyBundleManifestReportFields() {
    }

    public static final class Manifest {
        public static final String PRESENT = "present";
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String FINGERPRINT = "fingerprint";
        public static final String DETACHED = "detached";
        public static final String COUNT_CONSISTENCY_PROBLEMS = "countConsistencyProblems";
        public static final String BUNDLE_PRESET = "bundlePreset";
        public static final String REQUIRES_DIRECT_SAFETENSOR_RUNTIME = "requiresDirectSafetensorRuntime";
        public static final String PRODUCTION_SAFETY = "productionSafety";
        public static final String CATALOG_READINESS = "catalogReadiness";
        public static final String SELECTOR_SOURCE = "selectorSource";
        public static final String POLICY_SOURCE = "policySource";
        public static final String SELECTORS = "selectors";
        public static final String FAMILIES = "families";
        public static final String PROFILES = "profiles";
        public static final String REQUIRED_FAMILIES = "requiredFamilies";
        public static final String FORBIDDEN_FAMILIES = "forbiddenFamilies";
        public static final String REQUIRED_ALIASES = "requiredAliases";
        public static final String FORBIDDEN_ALIASES = "forbiddenAliases";
        public static final String POLICY_STATUS = "policyStatus";
        public static final String FIXTURE_STATUS = "fixtureStatus";
        public static final String ACTIVE_BUNDLE_PRESET_CONFORMANCE = "activeBundlePresetConformance";

        private Manifest() {
        }
    }

    public static final class ProductionSafety {
        public static final String TOKENIZER_METADATA_REQUIRED = "tokenizerMetadataRequired";
        public static final String TOKENIZER_METADATA_READY = "tokenizerMetadataReady";
        public static final String PASSED = "passed";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String PENDING_TOKENIZER_FAMILIES = "pendingTokenizerFamilies";
        public static final String PENDING_TOKENIZER_REASONS = "pendingTokenizerReasons";

        private ProductionSafety() {
        }
    }

    public static final class CatalogReadiness {
        public static final String STATUS_KNOWN = "statusKnown";
        public static final String PASSED = "passed";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String PRODUCTION_READINESS_PENDING_COUNT = "productionReadinessPendingCount";
        public static final String DIRECT_SAFETENSOR_PENDING_COUNT = "directSafetensorPendingCount";
        public static final String PRODUCTION_READINESS_PENDING_FAMILIES =
                "productionReadinessPendingFamilies";
        public static final String DIRECT_SAFETENSOR_PENDING_FAMILIES = "directSafetensorPendingFamilies";

        private CatalogReadiness() {
        }
    }

    public static final class PolicyStatus {
        public static final String KNOWN = "known";
        public static final String PASSED = "passed";
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";

        private PolicyStatus() {
        }
    }

    public static final class FixtureStatus {
        public static final String KNOWN = "known";
        public static final String PASSED = "passed";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String REQUIRED_SELECTORS = "requiredSelectors";
        public static final String REQUIRED_FAMILIES = "requiredFamilies";
        public static final String REQUIRED_FINGERPRINT = "requiredFingerprint";
        public static final String INVENTORY_FINGERPRINT = "inventoryFingerprint";
        public static final String AVAILABLE_FAMILY_COUNT = "availableFamilyCount";
        public static final String FIXTURE_FAMILY_COUNT = "fixtureFamilyCount";
        public static final String REQUIRED_FAMILY_COUNT = "requiredFamilyCount";
        public static final String REQUIRED_PASSED_COUNT = "requiredPassedCount";
        public static final String MISSING_REQUIRED_COUNT = "missingRequiredCount";
        public static final String PROBLEM_FAMILY_COUNT = "problemFamilyCount";
        public static final String MISSING_REQUIRED_FAMILIES = "missingRequiredFamilies";
        public static final String PROBLEM_FAMILIES = "problemFamilies";

        private FixtureStatus() {
        }
    }

    public static final class PresetConformance {
        public static final String PRESET_ID = "presetId";
        public static final String PRESET_METADATA_PRESENT = "presetMetadataPresent";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String MATCHES_PRESET = "matchesPreset";
        public static final String CLEAN_PRESET_BUILD = "cleanPresetBuild";
        public static final String SELECTORS_MATCH = "selectorsMatch";
        public static final String POLICY_INPUTS_MATCH = "policyInputsMatch";
        public static final String EXPLICIT_SELECTOR_OVERRIDE = "explicitSelectorOverride";
        public static final String EXPLICIT_POLICY_OVERRIDE = "explicitPolicyOverride";

        private PresetConformance() {
        }
    }

    public static List<String> presentOnlyFields() {
        return List.of(Manifest.PRESENT);
    }

    public static List<String> manifestFields() {
        return List.of(
                Manifest.PRESENT,
                Manifest.SCHEMA_VERSION,
                Manifest.FINGERPRINT,
                Manifest.DETACHED,
                Manifest.COUNT_CONSISTENCY_PROBLEMS,
                Manifest.BUNDLE_PRESET,
                Manifest.REQUIRES_DIRECT_SAFETENSOR_RUNTIME,
                Manifest.PRODUCTION_SAFETY,
                Manifest.CATALOG_READINESS,
                Manifest.SELECTOR_SOURCE,
                Manifest.POLICY_SOURCE,
                Manifest.SELECTORS,
                Manifest.FAMILIES,
                Manifest.PROFILES,
                Manifest.REQUIRED_FAMILIES,
                Manifest.FORBIDDEN_FAMILIES,
                Manifest.REQUIRED_ALIASES,
                Manifest.FORBIDDEN_ALIASES,
                Manifest.POLICY_STATUS,
                Manifest.FIXTURE_STATUS,
                Manifest.ACTIVE_BUNDLE_PRESET_CONFORMANCE);
    }

    public static List<String> productionSafetyFields() {
        return List.of(
                ProductionSafety.TOKENIZER_METADATA_REQUIRED,
                ProductionSafety.TOKENIZER_METADATA_READY,
                ProductionSafety.PASSED,
                ProductionSafety.STATUS,
                ProductionSafety.SUMMARY,
                ProductionSafety.PENDING_TOKENIZER_FAMILIES,
                ProductionSafety.PENDING_TOKENIZER_REASONS);
    }

    public static List<String> catalogReadinessFields() {
        return List.of(
                CatalogReadiness.STATUS_KNOWN,
                CatalogReadiness.PASSED,
                CatalogReadiness.STATUS,
                CatalogReadiness.SUMMARY,
                CatalogReadiness.PRODUCTION_READINESS_PENDING_COUNT,
                CatalogReadiness.DIRECT_SAFETENSOR_PENDING_COUNT,
                CatalogReadiness.PRODUCTION_READINESS_PENDING_FAMILIES,
                CatalogReadiness.DIRECT_SAFETENSOR_PENDING_FAMILIES);
    }

    public static List<String> policyStatusFields() {
        return List.of(
                PolicyStatus.KNOWN,
                PolicyStatus.PASSED,
                PolicyStatus.STATUS,
                PolicyStatus.VIOLATION_COUNT);
    }

    public static List<String> fixtureStatusFields() {
        return List.of(
                FixtureStatus.KNOWN,
                FixtureStatus.PASSED,
                FixtureStatus.STATUS,
                FixtureStatus.SUMMARY,
                FixtureStatus.REQUIRED_SELECTORS,
                FixtureStatus.REQUIRED_FAMILIES,
                FixtureStatus.REQUIRED_FINGERPRINT,
                FixtureStatus.INVENTORY_FINGERPRINT,
                FixtureStatus.AVAILABLE_FAMILY_COUNT,
                FixtureStatus.FIXTURE_FAMILY_COUNT,
                FixtureStatus.REQUIRED_FAMILY_COUNT,
                FixtureStatus.REQUIRED_PASSED_COUNT,
                FixtureStatus.MISSING_REQUIRED_COUNT,
                FixtureStatus.PROBLEM_FAMILY_COUNT,
                FixtureStatus.MISSING_REQUIRED_FAMILIES,
                FixtureStatus.PROBLEM_FAMILIES);
    }

    public static List<String> presetConformanceFields() {
        return List.of(
                PresetConformance.PRESET_ID,
                PresetConformance.PRESET_METADATA_PRESENT,
                PresetConformance.STATUS,
                PresetConformance.SUMMARY,
                PresetConformance.MATCHES_PRESET,
                PresetConformance.CLEAN_PRESET_BUILD,
                PresetConformance.SELECTORS_MATCH,
                PresetConformance.POLICY_INPUTS_MATCH,
                PresetConformance.EXPLICIT_SELECTOR_OVERRIDE,
                PresetConformance.EXPLICIT_POLICY_OVERRIDE);
    }
}
