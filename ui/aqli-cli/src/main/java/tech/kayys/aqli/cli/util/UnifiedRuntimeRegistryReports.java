/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.UnifiedRuntimeRegistryReportFields.RuntimeEntry;
import tech.kayys.aqli.cli.util.UnifiedRuntimeRegistryReportFields.Section;
import tech.kayys.aqli.cli.util.UnifiedRuntimeRegistryReportFields.Violation;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifest;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifestViolation;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds reusable unified-runtime registry sections for plugin and model-family reports.
 */
final class UnifiedRuntimeRegistryReports {
    private UnifiedRuntimeRegistryReports() {
    }

    static Map<String, Object> report(
            UnifiedRuntimeRegistry registry,
            List<UnifiedRuntimeRequirementCompatibility> requirementCompatibilities) {
        UnifiedRuntimeRegistry effectiveRegistry = registry == null
                ? UnifiedRuntimeRegistry.of(List.of())
                : registry;
        List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> runtimes = effectiveRegistry.reports();
        List<UnifiedRuntimeRequirementCompatibility> requirements = requirementCompatibilities == null
                ? List.of()
                : requirementCompatibilities;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Section.RUNTIME_COUNT, runtimes.size());
        report.put(Section.VALID_COUNT, runtimes.stream()
                .filter(UnifiedRuntimeRegistry.UnifiedRuntimeReport::valid)
                .count());
        report.put(Section.INVALID_COUNT, runtimes.stream()
                .filter(runtime -> !runtime.valid())
                .count());
        report.put(Section.PRODUCTION_READY_COUNT, runtimes.stream()
                .filter(runtime -> runtime.manifestAvailable()
                        && runtime.manifest().productionReady()
                        && runtime.valid())
                .count());
        report.put(Section.MODEL_TYPES, runtimes.stream()
                .filter(UnifiedRuntimeRegistry.UnifiedRuntimeReport::manifestAvailable)
                .flatMap(runtime -> runtime.manifest().modelTypes().stream())
                .distinct()
                .sorted()
                .toList());
        report.put(Section.RUNTIMES, runtimes.stream()
                .map(UnifiedRuntimeRegistryReports::runtimeReport)
                .toList());
        report.put(Section.CONFLICTS, effectiveRegistry.modelTypeConflicts().stream()
                .map(UnifiedRuntimeRegistryReports::violationReport)
                .toList());
        report.put(Section.CONTRACT_VIOLATIONS, runtimes.stream()
                .flatMap(runtime -> runtime.violations().stream())
                .map(UnifiedRuntimeRegistryReports::violationReport)
                .toList());
        report.putAll(UnifiedRuntimeRequirementReports.modelFamilyRequirementSection(requirements));
        return report;
    }

    private static Map<String, Object> runtimeReport(
            UnifiedRuntimeRegistry.UnifiedRuntimeReport runtime) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(RuntimeEntry.RUNTIME_ID, runtime.runtimeId());
        report.put(RuntimeEntry.VALID, runtime.valid());
        report.put(RuntimeEntry.MANIFEST_AVAILABLE, runtime.manifestAvailable());
        report.put(RuntimeEntry.DIAGNOSTICS, runtime.diagnostics());
        report.put(RuntimeEntry.VIOLATIONS, runtime.violations().stream()
                .map(UnifiedRuntimeRegistryReports::violationReport)
                .toList());
        UnifiedRuntimeManifest manifest = runtime.manifest();
        if (manifest == null) {
            report.put(RuntimeEntry.DISPLAY_NAME, runtime.runtimeId());
            report.put(RuntimeEntry.MODEL_FAMILY_IDS, List.of());
            report.put(RuntimeEntry.MODEL_TYPES, List.of());
            report.put(RuntimeEntry.INPUT_MODALITIES, List.of());
            report.put(RuntimeEntry.READINESS, "unavailable");
            report.put(RuntimeEntry.READINESS_REASON, runtime.diagnostics());
            report.put(RuntimeEntry.PRODUCTION_READY, false);
            return report;
        }
        report.put(RuntimeEntry.DISPLAY_NAME, manifest.displayName());
        report.put(RuntimeEntry.MODEL_FAMILY_IDS, manifest.modelFamilyIds());
        report.put(RuntimeEntry.MODEL_TYPES, manifest.modelTypes());
        report.put(RuntimeEntry.INPUT_MODALITIES, manifest.inputModalities().stream()
                .map(modality -> modality.name().toLowerCase(Locale.ROOT))
                .toList());
        report.put(RuntimeEntry.READINESS, manifest.readiness().statusLabel());
        report.put(RuntimeEntry.READINESS_REASON, manifest.readinessReason());
        report.put(RuntimeEntry.PRODUCTION_READY, manifest.productionReady() && runtime.valid());
        return report;
    }

    private static Map<String, Object> violationReport(
            UnifiedRuntimeManifestViolation violation) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Violation.RUNTIME_ID, violation.runtimeId());
        report.put(Violation.CODE, violation.code());
        report.put(Violation.MESSAGE, violation.message());
        report.put(Violation.SUMMARY, violation.summary());
        return report;
    }
}
