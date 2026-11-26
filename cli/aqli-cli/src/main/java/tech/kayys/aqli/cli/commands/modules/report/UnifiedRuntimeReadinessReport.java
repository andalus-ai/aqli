package tech.kayys.aqli.cli.commands.modules.report;

import tech.kayys.aqli.cli.util.UnifiedRuntimeRequirementCompatibility;
import tech.kayys.aqli.cli.util.UnifiedRuntimeRequirementReports;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifest;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifestViolation;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projects unified-runtime registry state into the CLI modules-report JSON section.
 *
 * <p>This class deliberately does not discover plugins or evaluate runtime policy;
 * callers supply the already-collected registry and requirement compatibility data.</p>
 */
public final class UnifiedRuntimeReadinessReport {
    private UnifiedRuntimeReadinessReport() {
    }

public static Map<String, Object> report(
            UnifiedRuntimeRegistry registry,
            String registryScope,
            List<UnifiedRuntimeRequirementCompatibility> requirementCompatibilities) {
        UnifiedRuntimeRegistry effectiveRegistry = registry == null
                ? UnifiedRuntimeRegistry.of(List.of())
                : registry;
        List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> runtimes = effectiveRegistry.reports();
        List<UnifiedRuntimeRequirementCompatibility> requirements = requirementCompatibilities == null
                ? List.of()
                : requirementCompatibilities;
        List<UnifiedRuntimeManifestViolation> conflicts = effectiveRegistry.modelTypeConflicts();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("registryScope", registryScope);
        section.put("runtimeCount", runtimes.size());
        section.put("validCount", runtimes.stream()
                .filter(UnifiedRuntimeRegistry.UnifiedRuntimeReport::valid)
                .count());
        section.put("invalidCount", runtimes.stream()
                .filter(report -> !report.valid())
                .count());
        section.put("productionReadyCount", runtimes.stream()
                .filter(report -> report.manifestAvailable()
                        && report.manifest().productionReady()
                        && report.valid())
                .count());
        section.put("modelTypes", runtimes.stream()
                .filter(UnifiedRuntimeRegistry.UnifiedRuntimeReport::manifestAvailable)
                .flatMap(report -> report.manifest().modelTypes().stream())
                .distinct()
                .sorted()
                .toList());
        section.put("runtimes", runtimes.stream()
                .map(UnifiedRuntimeReadinessReport::unifiedRuntimeReport)
                .toList());
        section.put("conflicts", conflicts.stream()
                .map(UnifiedRuntimeReadinessReport::unifiedRuntimeViolationReport)
                .toList());
        section.put("contractViolations", runtimes.stream()
                .flatMap(report -> report.violations().stream())
                .map(UnifiedRuntimeReadinessReport::unifiedRuntimeViolationReport)
                .toList());
        section.putAll(UnifiedRuntimeRequirementReports.modelFamilyRequirementSection(requirements));
        return section;
    }

private static Map<String, Object> unifiedRuntimeReport(
            UnifiedRuntimeRegistry.UnifiedRuntimeReport runtime) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runtimeId", runtime.runtimeId());
        report.put("valid", runtime.valid());
        report.put("manifestAvailable", runtime.manifestAvailable());
        report.put("diagnostics", runtime.diagnostics());
        report.put("violations", runtime.violations().stream()
                .map(UnifiedRuntimeReadinessReport::unifiedRuntimeViolationReport)
                .toList());
        UnifiedRuntimeManifest manifest = runtime.manifest();
        if (manifest == null) {
            report.put("displayName", runtime.runtimeId());
            report.put("modelFamilyIds", List.of());
            report.put("modelTypes", List.of());
            report.put("inputModalities", List.of());
            report.put("readiness", "unavailable");
            report.put("readinessReason", runtime.diagnostics());
            report.put("productionReady", false);
            report.put("requiredProcessorFiles", List.of());
            report.put("requiredTokenizerFiles", List.of());
            report.put("metadata", Map.of());
            return report;
        }
        report.put("displayName", manifest.displayName());
        report.put("modelFamilyIds", manifest.modelFamilyIds());
        report.put("modelTypes", manifest.modelTypes());
        report.put("inputModalities", manifest.inputModalities().stream()
                .map(modality -> modality.name().toLowerCase(Locale.ROOT))
                .toList());
        report.put("readiness", manifest.readiness().statusLabel());
        report.put("readinessReason", manifest.readinessReason());
        report.put("productionReady", manifest.productionReady() && runtime.valid());
        report.put("requiredProcessorFiles", manifest.requiredProcessorFiles());
        report.put("requiredTokenizerFiles", manifest.requiredTokenizerFiles());
        report.put("metadata", manifest.metadata());
        return report;
    }

private static Map<String, Object> unifiedRuntimeViolationReport(
            UnifiedRuntimeManifestViolation violation) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runtimeId", violation.runtimeId());
        report.put("code", violation.code());
        report.put("message", violation.message());
        report.put("summary", violation.summary());
        return report;
    }
}
