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
 * Stable JSON field names for model-family bundle availability sections in CI reports.
 */
public final class ModelFamilyBundleAvailabilityReportFields {
    private ModelFamilyBundleAvailabilityReportFields() {
    }

    public static final class Section {
        public static final String PRESENT = "present";
        public static final String DETACHED = "detached";
        public static final String HEALTHY = "healthy";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String SELECTED_FAMILY_COUNT = "selectedFamilyCount";
        public static final String DISCOVERED_SELECTED_FAMILY_COUNT = "discoveredSelectedFamilyCount";
        public static final String MISSING_SELECTED_FAMILY_COUNT = "missingSelectedFamilyCount";
        public static final String OMITTED_FAMILY_COUNT = "omittedFamilyCount";
        public static final String POLICY_STATUS = "policyStatus";
        public static final String POLICY_VIOLATION_COUNT = "policyViolationCount";
        public static final String PRODUCTION_SAFETY_STATUS = "productionSafetyStatus";
        public static final String PRODUCTION_SAFETY_PASSED = "productionSafetyPassed";
        public static final String PRODUCTION_PENDING_TOKENIZER_FAMILIES =
                "productionPendingTokenizerFamilies";
        public static final String CATALOG_READINESS_STATUS = "catalogReadinessStatus";
        public static final String CATALOG_READINESS_PASSED = "catalogReadinessPassed";
        public static final String PRODUCTION_READINESS_PENDING_COUNT = "productionReadinessPendingCount";
        public static final String DIRECT_SAFETENSOR_PENDING_COUNT = "directSafetensorPendingCount";
        public static final String PRODUCTION_READINESS_PENDING_FAMILIES =
                "productionReadinessPendingFamilies";
        public static final String DIRECT_SAFETENSOR_PENDING_FAMILIES = "directSafetensorPendingFamilies";
        public static final String FIXTURE_STATUS = "fixtureStatus";
        public static final String FIXTURE_PASSED = "fixturePassed";
        public static final String FIXTURE_MISSING_REQUIRED_COUNT = "fixtureMissingRequiredCount";
        public static final String FIXTURE_PROBLEM_FAMILY_COUNT = "fixtureProblemFamilyCount";
        public static final String PRESET_CONFORMANCE_STATUS = "presetConformanceStatus";
        public static final String PROBLEMS = "problems";
        public static final String REMEDIATION_HINTS = "remediationHints";
        public static final String MISSING_SELECTED_FAMILIES = "missingSelectedFamilies";
        public static final String OMITTED_FAMILIES = "omittedFamilies";
        public static final String FIXTURE_MISSING_REQUIRED_FAMILIES = "fixtureMissingRequiredFamilies";
        public static final String FIXTURE_PROBLEM_FAMILIES = "fixtureProblemFamilies";

        private Section() {
        }
    }

    public static List<String> fields() {
        return List.of(
                Section.PRESENT,
                Section.DETACHED,
                Section.HEALTHY,
                Section.STATUS,
                Section.SUMMARY,
                Section.SELECTED_FAMILY_COUNT,
                Section.DISCOVERED_SELECTED_FAMILY_COUNT,
                Section.MISSING_SELECTED_FAMILY_COUNT,
                Section.OMITTED_FAMILY_COUNT,
                Section.POLICY_STATUS,
                Section.POLICY_VIOLATION_COUNT,
                Section.PRODUCTION_SAFETY_STATUS,
                Section.PRODUCTION_SAFETY_PASSED,
                Section.PRODUCTION_PENDING_TOKENIZER_FAMILIES,
                Section.CATALOG_READINESS_STATUS,
                Section.CATALOG_READINESS_PASSED,
                Section.PRODUCTION_READINESS_PENDING_COUNT,
                Section.DIRECT_SAFETENSOR_PENDING_COUNT,
                Section.PRODUCTION_READINESS_PENDING_FAMILIES,
                Section.DIRECT_SAFETENSOR_PENDING_FAMILIES,
                Section.FIXTURE_STATUS,
                Section.FIXTURE_PASSED,
                Section.FIXTURE_MISSING_REQUIRED_COUNT,
                Section.FIXTURE_PROBLEM_FAMILY_COUNT,
                Section.PRESET_CONFORMANCE_STATUS,
                Section.PROBLEMS,
                Section.REMEDIATION_HINTS,
                Section.MISSING_SELECTED_FAMILIES,
                Section.OMITTED_FAMILIES,
                Section.FIXTURE_MISSING_REQUIRED_FAMILIES,
                Section.FIXTURE_PROBLEM_FAMILIES);
    }
}
