/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.ModelFamilyBundleAvailabilityReportFields.Section;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the reusable model-family bundle availability section for CI reports.
 */
final class ModelFamilyBundleAvailabilityReports {
    private ModelFamilyBundleAvailabilityReports() {
    }

    static Map<String, Object> availability(
            PluginAvailabilityChecker.ModelFamilyBundleAvailability availability) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Section.PRESENT, availability.present());
        report.put(Section.DETACHED, availability.detached());
        report.put(Section.HEALTHY, availability.healthy());
        report.put(Section.STATUS, availability.status());
        report.put(Section.SUMMARY, availability.compactSummary());
        report.put(Section.SELECTED_FAMILY_COUNT, availability.selectedFamilyCount());
        report.put(Section.DISCOVERED_SELECTED_FAMILY_COUNT, availability.discoveredSelectedFamilyCount());
        report.put(Section.MISSING_SELECTED_FAMILY_COUNT, availability.missingSelectedFamilyCount());
        report.put(Section.OMITTED_FAMILY_COUNT, availability.omittedFamilyCount());
        report.put(Section.POLICY_STATUS, availability.policyStatus());
        report.put(Section.POLICY_VIOLATION_COUNT, availability.policyViolationCount());
        report.put(Section.PRODUCTION_SAFETY_STATUS, availability.productionSafetyStatus());
        report.put(Section.PRODUCTION_SAFETY_PASSED, availability.productionSafetyPassed());
        report.put(Section.PRODUCTION_PENDING_TOKENIZER_FAMILIES, availability.productionPendingTokenizerFamilies());
        report.put(Section.CATALOG_READINESS_STATUS, availability.catalogReadinessStatus());
        report.put(Section.CATALOG_READINESS_PASSED, availability.catalogReadinessPassed());
        report.put(Section.PRODUCTION_READINESS_PENDING_COUNT, availability.productionReadinessPendingCount());
        report.put(Section.DIRECT_SAFETENSOR_PENDING_COUNT, availability.directSafetensorPendingCount());
        report.put(Section.PRODUCTION_READINESS_PENDING_FAMILIES,
                availability.productionReadinessPendingFamilies());
        report.put(Section.DIRECT_SAFETENSOR_PENDING_FAMILIES, availability.directSafetensorPendingFamilies());
        report.put(Section.FIXTURE_STATUS, availability.fixtureStatus());
        report.put(Section.FIXTURE_PASSED, availability.fixturePassed());
        report.put(Section.FIXTURE_MISSING_REQUIRED_COUNT, availability.fixtureMissingRequiredCount());
        report.put(Section.FIXTURE_PROBLEM_FAMILY_COUNT, availability.fixtureProblemFamilyCount());
        report.put(Section.PRESET_CONFORMANCE_STATUS, availability.presetConformanceStatus());
        report.put(Section.PROBLEMS, availability.problems());
        report.put(Section.REMEDIATION_HINTS, availability.remediationHints());
        report.put(Section.MISSING_SELECTED_FAMILIES, availability.missingSelectedFamilies());
        report.put(Section.OMITTED_FAMILIES, availability.omittedFamilies());
        report.put(Section.FIXTURE_MISSING_REQUIRED_FAMILIES, availability.fixtureMissingRequiredFamilies());
        report.put(Section.FIXTURE_PROBLEM_FAMILIES, availability.fixtureProblemFamilies());
        return report;
    }
}
