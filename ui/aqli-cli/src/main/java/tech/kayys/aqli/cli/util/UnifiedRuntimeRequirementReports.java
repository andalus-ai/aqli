/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared report shaping for model-family unified runtime requirements.
 */
public final class UnifiedRuntimeRequirementReports {
    private UnifiedRuntimeRequirementReports() {
    }

    public static Map<String, Object> totals(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        List<UnifiedRuntimeRequirementCompatibility> safeRequirements = safeRequirements(requirements);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(UnifiedRuntimeRequirementReportFields.Totals.REQUIREMENT_COUNT, safeRequirements.size());
        report.put(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_COUNT, safeRequirements.stream()
                .filter(UnifiedRuntimeRequirementCompatibility::compatible)
                .count());
        report.put(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_COUNT, safeRequirements.stream()
                .filter(UnifiedRuntimeRequirementCompatibility::requiresAttention)
                .count());
        report.put(UnifiedRuntimeRequirementReportFields.Totals.FAMILY_IDS,
                familyIds(safeRequirements, requirement -> true));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_FAMILY_IDS,
                familyIds(safeRequirements, UnifiedRuntimeRequirementCompatibility::compatible));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_FAMILY_IDS,
                familyIds(safeRequirements, UnifiedRuntimeRequirementCompatibility::requiresAttention));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.MODEL_TYPES,
                modelTypes(safeRequirements, requirement -> true));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_MODEL_TYPES,
                modelTypes(safeRequirements, UnifiedRuntimeRequirementCompatibility::compatible));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_MODEL_TYPES,
                modelTypes(safeRequirements, UnifiedRuntimeRequirementCompatibility::requiresAttention));
        Map<String, Long> byStatus = safeRequirements.stream()
                .collect(Collectors.groupingBy(
                        UnifiedRuntimeRequirementCompatibility::status,
                        TreeMap::new,
                        Collectors.counting()));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.BY_STATUS, new LinkedHashMap<>(byStatus));
        Map<String, Long> problemCodeCounts = problemCodeCounts(safeRequirements);
        report.put(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODES,
                List.copyOf(problemCodeCounts.keySet()));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODE_COUNTS,
                new LinkedHashMap<>(problemCodeCounts));
        Map<String, Long> remediationHintCounts = remediationHintCounts(safeRequirements);
        report.put(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINTS,
                List.copyOf(remediationHintCounts.keySet()));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINT_COUNTS,
                new LinkedHashMap<>(remediationHintCounts));
        report.put(UnifiedRuntimeRequirementReportFields.Totals.ISSUES, issueSummaries(safeRequirements));
        return report;
    }

    public static List<Map<String, Object>> compatibilityReports(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return safeRequirements(requirements).stream()
                .map(UnifiedRuntimeRequirementReports::compatibilityReport)
                .toList();
    }

    public static Map<String, Object> modelFamilyRequirementSection(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        Map<String, Object> totals = totals(requirements);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
                UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION,
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION);
        report.put(
                UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA,
                schemaReport());
        report.put(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS, totals);
        report.put(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS,
                UnifiedRuntimeRequirementRecommendations.fromTotals(totals));
        report.put(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS,
                compatibilityReports(requirements));
        report.put(
                UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_CONTRACT,
                UnifiedRuntimeRequirementReportContract.report(report));
        return report;
    }

    public static Map<String, Object> schemaReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.CONTRACT_ID,
                UnifiedRuntimeRequirementReportFields.CONTRACT_ID);
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_VERSION,
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION);
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_FINGERPRINT,
                UnifiedRuntimeRequirementReportFields.schemaFingerprint());
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.SECTION_KEYS,
                UnifiedRuntimeRequirementReportFields.sectionKeys());
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.TOTALS_KEYS,
                UnifiedRuntimeRequirementReportFields.totalsKeys());
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.REQUIREMENT_KEYS,
                UnifiedRuntimeRequirementReportFields.requirementKeys());
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.ISSUE_KEYS,
                UnifiedRuntimeRequirementReportFields.issueKeys());
        report.put(
                UnifiedRuntimeRequirementReportFields.Schema.AFFECTED_REQUIREMENT_KEYS,
                UnifiedRuntimeRequirementReportFields.affectedRequirementKeys());
        return report;
    }

    public static Map<String, Object> compatibilityReport(
            UnifiedRuntimeRequirementCompatibility compatibility) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID, compatibility.familyId());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE, compatibility.modelType());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.REQUIRED_INPUT_MODALITIES,
                compatibility.requiredInputModalities());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.PRODUCTION_READY_REQUIRED,
                compatibility.productionReadyRequired());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.STATUS, compatibility.status());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.COMPATIBLE, compatibility.compatible());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.RUNTIME_IDS, compatibility.runtimeIds());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.AVAILABLE_INPUT_MODALITIES,
                compatibility.availableInputModalities());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.PROBLEM_CODES,
                compatibility.effectiveProblemCodes());
        report.put(UnifiedRuntimeRequirementReportFields.Requirement.REMEDIATION_HINTS,
                compatibility.effectiveRemediationHints());
        return report;
    }

    private static List<UnifiedRuntimeRequirementCompatibility> safeRequirements(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return requirements == null
                ? List.of()
                : requirements.stream()
                        .filter(Objects::nonNull)
                        .toList();
    }

    private static Map<String, Long> problemCodeCounts(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return requirements.stream()
                .flatMap(requirement -> requirement.effectiveProblemCodes().stream())
                .filter(code -> code != null && !code.isBlank())
                .map(UnifiedRuntimeRequirementIssueKind::canonicalProblemCode)
                .collect(Collectors.groupingBy(
                        code -> code,
                        TreeMap::new,
                        Collectors.counting()));
    }

    private static Map<String, Long> remediationHintCounts(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return requirements.stream()
                .flatMap(requirement -> requirement.effectiveRemediationHints().stream())
                .filter(hint -> hint != null && !hint.isBlank())
                .map(String::trim)
                .collect(Collectors.groupingBy(
                        hint -> hint,
                        TreeMap::new,
                        Collectors.counting()));
    }

    private static List<Map<String, Object>> issueSummaries(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        Map<String, List<UnifiedRuntimeRequirementCompatibility>> byProblemCode = new TreeMap<>();
        for (UnifiedRuntimeRequirementCompatibility requirement : requirements) {
            for (String problemCode : requirement.effectiveProblemCodes()) {
                String canonical = UnifiedRuntimeRequirementIssueKind.canonicalProblemCode(problemCode);
                if (!canonical.isBlank()) {
                    byProblemCode.computeIfAbsent(canonical, ignored -> new ArrayList<>())
                            .add(requirement);
                }
            }
        }
        return byProblemCode.entrySet().stream()
                .map(entry -> issueSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, Object> issueSummary(
            String problemCode,
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(UnifiedRuntimeRequirementReportFields.Issue.PROBLEM_CODE, problemCode);
        report.put(UnifiedRuntimeRequirementReportFields.Issue.STATUS,
                UnifiedRuntimeRequirementIssueKind.fromProblemCode(problemCode)
                .map(UnifiedRuntimeRequirementIssueKind::status)
                .orElse(problemCode));
        report.put(UnifiedRuntimeRequirementReportFields.Issue.COUNT, (long) requirements.size());
        report.put(UnifiedRuntimeRequirementReportFields.Issue.AFFECTED_REQUIREMENTS,
                affectedRequirements(requirements));
        report.put(UnifiedRuntimeRequirementReportFields.Issue.FAMILY_IDS,
                familyIds(requirements, requirement -> true));
        report.put(UnifiedRuntimeRequirementReportFields.Issue.MODEL_TYPES,
                modelTypes(requirements, requirement -> true));
        report.put(UnifiedRuntimeRequirementReportFields.Issue.REMEDIATION_HINTS, requirements.stream()
                .flatMap(requirement -> requirement.effectiveRemediationHints().stream())
                .filter(hint -> hint != null && !hint.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList());
        return report;
    }

    private static List<Map<String, Object>> affectedRequirements(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return requirements.stream()
                .map(requirement -> {
                    Map<String, Object> report = new LinkedHashMap<>();
                    report.put(UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID,
                            requirement.familyId());
                    report.put(UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE,
                            requirement.modelType());
                    report.put(UnifiedRuntimeRequirementReportFields.Requirement.REQUIRED_INPUT_MODALITIES,
                            requirement.requiredInputModalities());
                    report.put(UnifiedRuntimeRequirementReportFields.Requirement.RUNTIME_IDS,
                            requirement.runtimeIds());
                    report.put(UnifiedRuntimeRequirementReportFields.Requirement.AVAILABLE_INPUT_MODALITIES,
                            requirement.availableInputModalities());
                    return report;
                })
                .toList();
    }

    private static List<String> familyIds(
            List<UnifiedRuntimeRequirementCompatibility> requirements,
            Predicate<UnifiedRuntimeRequirementCompatibility> filter) {
        return requirements.stream()
                .filter(filter)
                .map(UnifiedRuntimeRequirementCompatibility::familyId)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> modelTypes(
            List<UnifiedRuntimeRequirementCompatibility> requirements,
            Predicate<UnifiedRuntimeRequirementCompatibility> filter) {
        return requirements.stream()
                .filter(filter)
                .map(UnifiedRuntimeRequirementCompatibility::modelType)
                .distinct()
                .sorted()
                .toList();
    }
}
