/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.PluginGatesReportFields.Gate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds reusable combined plugin-gate sections for CLI and CI reports.
 */
final class PluginGatesReports {
    private PluginGatesReports() {
    }

    static Map<String, Object> gate(PluginGates gates) {
        PluginGates effectiveGates = gates == null
                ? PluginGates.evaluate(null, null)
                : gates;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Gate.PASSED, effectiveGates.passed());
        report.put(Gate.FAILED, effectiveGates.failed());
        report.put(Gate.STATUS, effectiveGates.status());
        report.put(Gate.VIOLATION_COUNT, effectiveGates.violationCount());
        report.put(Gate.VIOLATIONS, effectiveGates.violations());
        report.put(Gate.VIOLATION_CATEGORIES, PluginGateViolationReports.categories(effectiveGates));
        report.put(Gate.EXTENSION_STATUS, effectiveGates.extensionStatus());
        report.put(Gate.MODEL_FAMILY_STATUS, effectiveGates.modelFamilyStatus());
        report.put(Gate.EXTENSION_VIOLATION_COUNT, effectiveGates.extensionViolationCount());
        report.put(Gate.MODEL_FAMILY_VIOLATION_COUNT, effectiveGates.modelFamilyViolationCount());
        report.put(Gate.MODEL_FAMILY_CONTRACT_CATEGORY_COUNTS,
                effectiveGates.modelFamilyContractCategoryCounts());
        report.put(Gate.MODEL_FAMILY_CONTRACT_REMEDIATION_HINTS,
                effectiveGates.modelFamilyContractRemediationHints());
        return report;
    }
}
