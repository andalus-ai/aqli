/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifestViolation;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combined release gate for all detachable Aqli plugin surfaces.
 */
public record PluginGates(
        boolean passed,
        String status,
        int violationCount,
        List<String> violations,
        String extensionStatus,
        String modelFamilyStatus,
        int extensionViolationCount,
        int modelFamilyViolationCount,
        Map<String, Integer> modelFamilyContractCategoryCounts,
        List<String> modelFamilyContractRemediationHints) {

    public PluginGates {
        status = status == null || status.isBlank() ? (passed ? "passed" : "failed") : status;
        violations = List.copyOf(violations == null ? List.of() : violations);
        violationCount = Math.max(0, violationCount);
        extensionStatus = extensionStatus == null || extensionStatus.isBlank() ? "unknown" : extensionStatus;
        modelFamilyStatus = modelFamilyStatus == null || modelFamilyStatus.isBlank()
                ? "unknown"
                : modelFamilyStatus;
        extensionViolationCount = Math.max(0, extensionViolationCount);
        modelFamilyViolationCount = Math.max(0, modelFamilyViolationCount);
        modelFamilyContractCategoryCounts = Collections.unmodifiableMap(new LinkedHashMap<>(
                modelFamilyContractCategoryCounts == null ? Map.of() : modelFamilyContractCategoryCounts));
        modelFamilyContractRemediationHints = List.copyOf(
                modelFamilyContractRemediationHints == null ? List.of() : modelFamilyContractRemediationHints);
    }

    public PluginGates(
            boolean passed,
            String status,
            int violationCount,
            List<String> violations,
            String extensionStatus,
            String modelFamilyStatus,
            int extensionViolationCount,
            int modelFamilyViolationCount) {
        this(
                passed,
                status,
                violationCount,
                violations,
                extensionStatus,
                modelFamilyStatus,
                extensionViolationCount,
                modelFamilyViolationCount,
                Map.of(),
                List.of());
    }

    public static PluginGates evaluate(
            ExtensionAvailabilityGate extensionGate,
            ModelFamilyBundleGate modelFamilyGate) {
        boolean extensionPassed = extensionGate != null && extensionGate.passed();
        boolean modelFamilyPassed = modelFamilyGate != null && modelFamilyGate.passed();

        List<String> violations = new ArrayList<>();
        if (extensionGate == null) {
            violations.add("extension: extension availability gate was not evaluated");
        } else {
            extensionGate.violations().stream()
                    .map(violation -> "extension: " + violation)
                    .forEach(violations::add);
        }
        if (modelFamilyGate == null) {
            violations.add("model-family: model-family bundle gate was not evaluated");
        } else {
            modelFamilyGate.violations().stream()
                    .map(violation -> "model-family: " + violation)
                    .forEach(violations::add);
        }

        String status = gateStatus(extensionPassed, modelFamilyPassed, modelFamilyGate);
        return new PluginGates(
                extensionPassed && modelFamilyPassed,
                status,
                violations.size(),
                violations,
                extensionGate == null ? "unknown" : extensionGate.status(),
                modelFamilyGate == null ? "unknown" : modelFamilyGate.status(),
                extensionGate == null ? 1 : extensionGate.violationCount(),
                modelFamilyGate == null ? 1 : modelFamilyGate.violationCount(),
                modelFamilyGate == null ? Map.of() : modelFamilyGate.contractCategoryCounts(),
                modelFamilyGate == null ? List.of() : modelFamilyGate.contractRemediationHints());
    }

    private static String gateStatus(
            boolean extensionPassed,
            boolean modelFamilyPassed,
            ModelFamilyBundleGate modelFamilyGate) {
        if (extensionPassed && modelFamilyPassed) {
            return "passed";
        }
        if (!extensionPassed && modelFamilyPassed) {
            return "extension_failed";
        }
        String modelFamilyScope = modelFamilyFailureScope(modelFamilyGate);
        if (extensionPassed) {
            return modelFamilyScope + "_failed";
        }
        return "extension_and_" + modelFamilyScope + "_failed";
    }

    private static String modelFamilyFailureScope(ModelFamilyBundleGate modelFamilyGate) {
        if (modelFamilyGate == null) {
            return "model_family";
        }
        String status = modelFamilyGate.status();
        if ("production_safety_failed".equals(status)) {
            return "model_family_production_safety";
        }
        if ("catalog_readiness_failed".equals(status)) {
            return "model_family_catalog_readiness";
        }
        if (status != null && status.startsWith("production_safety_and_") && status.endsWith("_failed")) {
            return "model_family_" + status.substring(0, status.length() - "_failed".length());
        }
        if (status != null && status.startsWith("catalog_readiness_and_") && status.endsWith("_failed")) {
            return "model_family_" + status.substring(0, status.length() - "_failed".length());
        }
        return "model_family";
    }

    public static PluginGates withPluginDirectoryReadiness(
            PluginGates gates,
            ExternalPluginClasspath.PluginDirectoryInspection inspection) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        if (inspection == null) {
            return base;
        }
        List<String> readinessViolations = pluginDirectoryReadinessViolations(inspection);
        if (readinessViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(readinessViolations);
        return new PluginGates(
                false,
                pluginDirectoryGateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    public static PluginGates withUnifiedRuntimeReadiness(
            PluginGates gates,
            UnifiedRuntimeRegistry registry) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        if (registry == null) {
            return base;
        }
        List<String> runtimeViolations = unifiedRuntimeReadinessViolations(registry);
        if (runtimeViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(runtimeViolations);
        return new PluginGates(
                false,
                unifiedRuntimeGateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    public static PluginGates withUnifiedRuntimeRequirements(
            PluginGates gates,
            List<UnifiedRuntimeRequirementCompatibility> compatibilities) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> requirementViolations = unifiedRuntimeRequirementViolations(compatibilities);
        if (requirementViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(requirementViolations);
        return new PluginGates(
                false,
                unifiedRuntimeRequirementGateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    public static PluginGates withUnifiedRuntimeRequirementReportContract(
            PluginGates gates,
            Map<?, ?> reportSection) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> contractViolations = unifiedRuntimeRequirementReportContractViolations(reportSection);
        if (contractViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(contractViolations);
        return new PluginGates(
                false,
                unifiedRuntimeRequirementGateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    public static PluginGates withModelFamilyContractReportContract(
            PluginGates gates,
            Map<?, ?> reportSection) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> contractViolations = modelFamilyContractReportViolations(reportSection);
        if (contractViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(contractViolations);
        return new PluginGates(
                false,
                modelFamilyContractReportGateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    private static List<String> unifiedRuntimeReadinessViolations(UnifiedRuntimeRegistry registry) {
        List<String> violations = new ArrayList<>();
        for (UnifiedRuntimeManifestViolation conflict : registry.modelTypeConflicts()) {
            violations.add("unified-runtime: " + conflict.summary());
        }
        for (UnifiedRuntimeRegistry.UnifiedRuntimeReport report : registry.reports()) {
            for (UnifiedRuntimeManifestViolation violation : report.violations()) {
                violations.add("unified-runtime: " + violation.summary());
            }
        }
        return List.copyOf(violations);
    }

    private static List<String> unifiedRuntimeRequirementViolations(
            List<UnifiedRuntimeRequirementCompatibility> compatibilities) {
        if (compatibilities == null || compatibilities.isEmpty()) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        for (UnifiedRuntimeRequirementCompatibility compatibility : compatibilities) {
            if (!compatibility.requiresAttention()) {
                continue;
            }
            violations.add("unified-runtime-requirement: "
                    + compatibility.familyId()
                    + " -> "
                    + compatibility.modelType()
                    + " status="
                    + compatibility.status()
                    + " problems="
                    + joinOrNone(compatibility.effectiveProblemCodes())
                    + " requiredModalities="
                    + joinOrNone(compatibility.requiredInputModalities())
                    + " runtimeIds="
                    + joinOrNone(compatibility.runtimeIds()));
        }
        return List.copyOf(violations);
    }

    private static List<String> unifiedRuntimeRequirementReportContractViolations(Map<?, ?> reportSection) {
        List<String> problems = UnifiedRuntimeRequirementReportContract.validateSection(reportSection);
        if (problems.isEmpty()) {
            return List.of();
        }
        return problems.stream()
                .map(problem -> "unified-runtime-requirement: report contract failed: " + problem)
                .toList();
    }

    private static List<String> modelFamilyContractReportViolations(Map<?, ?> reportSection) {
        List<String> problems = ModelFamilyContractViolationReports.validateSummary(reportSection);
        if (problems.isEmpty()) {
            return List.of();
        }
        return problems.stream()
                .map(problem -> "model-family: contract report failed: " + problem)
                .toList();
    }

    private static List<String> pluginDirectoryReadinessViolations(
            ExternalPluginClasspath.PluginDirectoryInspection inspection) {
        List<String> violations = new ArrayList<>();
        for (String error : inspection.errors()) {
            violations.add("plugin-directory: " + error);
        }
        for (ExternalPluginClasspath.PluginDirectoryJarReport jar : inspection.jars()) {
            if (!jar.inspectionError().isBlank()) {
                violations.add("plugin-directory: plugin jar is not inspectable: "
                        + jar.path() + " (" + jar.inspectionError() + ")");
            } else if (jar.pluginInstallCandidate() && !jar.pluginInstallReady()) {
                violations.add("plugin-directory: model-family plugin jar is not plugin-core ready: "
                        + jar.path() + " (" + String.join("; ", jar.pluginInstallErrors()) + ")");
            } else if (jar.hasUnifiedMultimodalRuntimeServiceEntry() && !jar.unifiedRuntimeReady()) {
                violations.add("plugin-directory: unified multimodal runtime jar is not ServiceLoader ready: "
                        + jar.path() + " (" + String.join("; ", jar.unifiedRuntimeErrors()) + ")");
            }
        }
        return List.copyOf(violations);
    }

    private static String pluginDirectoryGateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "plugin_directory_failed";
        }
        if (base.status().contains("plugin_directory")) {
            return base.status();
        }
        return base.status() + "_and_plugin_directory_failed";
    }

    private static String unifiedRuntimeGateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "unified_runtime_failed";
        }
        if (base.status().contains("unified_runtime")) {
            return base.status();
        }
        return base.status() + "_and_unified_runtime_failed";
    }

    private static String unifiedRuntimeRequirementGateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "unified_runtime_requirement_failed";
        }
        if (base.status().contains("unified_runtime_requirement")) {
            return base.status();
        }
        return base.status() + "_and_unified_runtime_requirement_failed";
    }

    private static String modelFamilyContractReportGateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "model_family_contract_report_failed";
        }
        if (base.status().contains("model_family_contract_report")) {
            return base.status();
        }
        return base.status() + "_and_model_family_contract_report_failed";
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(",", values);
    }

    public boolean failed() {
        return !passed;
    }

    public String failureMessage() {
        String details = violations.isEmpty()
                ? ""
                : "\n  - " + String.join("\n  - ", violations);
        String recommendations = modelFamilyContractRemediationHints.isEmpty()
                ? ""
                : "\n  Model-family contract recommendations:\n  - "
                        + String.join("\n  - ", modelFamilyContractRemediationHints);
        return "Aqli plugin gates failed: " + status + details + recommendations;
    }
}
