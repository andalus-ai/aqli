/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Actionable guidance for selected model-family unified runtime requirements.
 */
public final class UnifiedRuntimeRequirementRecommendations {
    private static final int DISPLAY_LIMIT = 4;

    private UnifiedRuntimeRequirementRecommendations() {
    }

    public static List<String> fromRequirements(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        Map<String, List<UnifiedRuntimeRequirementCompatibility>> byProblemCode =
                problemCodeGroups(requirements);
        if (byProblemCode.isEmpty()) {
            return List.of();
        }
        List<String> recommendations = new ArrayList<>();
        for (String problemCode : orderedProblemCodes(byProblemCode)) {
            recommendations.add(recommendation(problemCode, byProblemCode.get(problemCode)));
        }
        return List.copyOf(recommendations);
    }

    public static List<String> fromTotals(Map<String, Object> totals) {
        Map<String, List<Map<?, ?>>> byProblemCode = issueGroups(totals);
        if (byProblemCode.isEmpty()) {
            return List.of();
        }
        List<String> recommendations = new ArrayList<>();
        for (String problemCode : orderedProblemCodes(byProblemCode)) {
            recommendations.add(recommendationFromIssues(problemCode, byProblemCode.get(problemCode)));
        }
        return List.copyOf(recommendations);
    }

    private static Map<String, List<UnifiedRuntimeRequirementCompatibility>> problemCodeGroups(
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        Map<String, List<UnifiedRuntimeRequirementCompatibility>> groups = new TreeMap<>();
        if (requirements == null) {
            return groups;
        }
        for (UnifiedRuntimeRequirementCompatibility requirement : requirements) {
            if (requirement == null || !requirement.requiresAttention()) {
                continue;
            }
            for (String problemCode : requirement.effectiveProblemCodes()) {
                String normalized = UnifiedRuntimeRequirementIssueKind.canonicalProblemCode(problemCode);
                if (!normalized.isBlank()) {
                    groups.computeIfAbsent(normalized, ignored -> new ArrayList<>())
                            .add(requirement);
                }
            }
        }
        return groups;
    }

    private static Map<String, List<Map<?, ?>>> issueGroups(Map<String, Object> totals) {
        Map<String, List<Map<?, ?>>> groups = new TreeMap<>();
        if (totals == null) {
            return groups;
        }
        Object issues = totals.get(UnifiedRuntimeRequirementReportFields.Totals.ISSUES);
        if (!(issues instanceof List<?> issueReports)) {
            return groups;
        }
        for (Object issueReport : issueReports) {
            if (!(issueReport instanceof Map<?, ?> issue)) {
                continue;
            }
            String problemCode = UnifiedRuntimeRequirementIssueKind.canonicalProblemCode(
                    issueText(issue, UnifiedRuntimeRequirementReportFields.Issue.PROBLEM_CODE, ""));
            if (!problemCode.isBlank()) {
                groups.computeIfAbsent(problemCode, ignored -> new ArrayList<>())
                        .add(issue);
            }
        }
        return groups;
    }

    private static List<String> orderedProblemCodes(Map<String, ?> groups) {
        Map<String, ?> remaining = new LinkedHashMap<>(groups);
        List<String> ordered = new ArrayList<>();
        for (String problemCode : UnifiedRuntimeRequirementIssueKind.problemCodesInDeclarationOrder()) {
            if (remaining.containsKey(problemCode)) {
                ordered.add(problemCode);
                remaining.remove(problemCode);
            }
        }
        ordered.addAll(remaining.keySet());
        return ordered;
    }

    private static String recommendation(
            String problemCode,
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return UnifiedRuntimeRequirementIssueKind.fromProblemCode(problemCode)
                .map(issueKind -> recommendation(issueKind, requirements))
                .orElseGet(() -> "Resolve unified runtime requirement problem " + problemCode + " for: "
                        + requirementPairs(requirements) + ".");
    }

    private static String recommendationFromIssues(
            String problemCode,
            List<Map<?, ?>> issues) {
        return UnifiedRuntimeRequirementIssueKind.fromProblemCode(problemCode)
                .map(issueKind -> recommendationFromIssues(issueKind, issues))
                .orElseGet(() -> "Resolve unified runtime requirement problem " + problemCode + " for: "
                        + issueRequirementPairs(issues) + ".");
    }

    private static String recommendation(
            UnifiedRuntimeRequirementIssueKind issueKind,
            List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return switch (issueKind) {
            case MISSING_RUNTIME -> "Attach unified runtime plugins for "
                    + "missing selected model-family "
                    + "requirements: " + requirementPairs(requirements) + ".";
            case CONFLICTING_RUNTIME -> "Detach duplicate unified "
                    + "runtime plugins or split "
                    + "model_type claims for selected requirements: " + modelTypes(requirements) + ".";
            case INVALID_RUNTIME -> "Fix unified runtime manifest violations "
                    + "before serving "
                    + "selected requirements: " + runtimeIdsOrPairs(requirements) + ".";
            case INSUFFICIENT_MODALITIES -> "Attach unified runtimes "
                    + "with all required "
                    + "modalities for selected requirements: " + modalityDetails(requirements) + ".";
            case NOT_PRODUCTION_READY -> "Keep selected model families out "
                    + "of production bundles "
                    + "or attach production-ready unified runtimes for: " + requirementPairs(requirements) + ".";
        };
    }

    private static String recommendationFromIssues(
            UnifiedRuntimeRequirementIssueKind issueKind,
            List<Map<?, ?>> issues) {
        return switch (issueKind) {
            case MISSING_RUNTIME -> "Attach unified runtime plugins for "
                    + "missing selected model-family "
                    + "requirements: " + issueRequirementPairs(issues) + ".";
            case CONFLICTING_RUNTIME -> "Detach duplicate unified "
                    + "runtime plugins or split "
                    + "model_type claims for selected requirements: " + issueModelTypes(issues) + ".";
            case INVALID_RUNTIME -> "Fix unified runtime manifest violations "
                    + "before serving "
                    + "selected requirements: " + issueRuntimeIdsOrPairs(issues) + ".";
            case INSUFFICIENT_MODALITIES -> "Attach unified runtimes "
                    + "with all required "
                    + "modalities for selected requirements: " + issueModalityDetails(issues) + ".";
            case NOT_PRODUCTION_READY -> "Keep selected model families out "
                    + "of production bundles "
                    + "or attach production-ready unified runtimes for: " + issueRequirementPairs(issues) + ".";
        };
    }

    private static String requirementPairs(List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return joinedLimited(requirements.stream()
                .map(requirement -> requirement.familyId() + "->" + requirement.modelType())
                .distinct()
                .sorted()
                .toList());
    }

    private static String modelTypes(List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return joinedLimited(requirements.stream()
                .map(UnifiedRuntimeRequirementCompatibility::modelType)
                .distinct()
                .sorted()
                .toList());
    }

    private static String runtimeIdsOrPairs(List<UnifiedRuntimeRequirementCompatibility> requirements) {
        List<String> runtimeIds = requirements.stream()
                .flatMap(requirement -> requirement.runtimeIds().stream())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        return runtimeIds.isEmpty() ? requirementPairs(requirements) : joinedLimited(runtimeIds);
    }

    private static String modalityDetails(List<UnifiedRuntimeRequirementCompatibility> requirements) {
        return joinedLimited(requirements.stream()
                .map(UnifiedRuntimeRequirementRecommendations::modalityDetail)
                .distinct()
                .sorted()
                .toList());
    }

    private static String modalityDetail(UnifiedRuntimeRequirementCompatibility requirement) {
        String detail = requirement.familyId() + "->" + requirement.modelType()
                + " needs " + joinValues(requirement.requiredInputModalities());
        if (!requirement.availableInputModalities().isEmpty()) {
            detail += " (available " + joinValues(requirement.availableInputModalities()) + ")";
        }
        return detail;
    }

    private static String issueRequirementPairs(List<Map<?, ?>> issues) {
        List<String> pairs = affectedRequirements(issues).stream()
                .map(requirement -> issueText(
                                requirement,
                                UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID,
                                "unknown")
                        + "->" + issueText(
                                requirement,
                                UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE,
                                "unknown"))
                .distinct()
                .sorted()
                .toList();
        if (!pairs.isEmpty()) {
            return joinedLimited(pairs);
        }
        return "families=" + joinedLimited(issueValues(
                        issues,
                        UnifiedRuntimeRequirementReportFields.Issue.FAMILY_IDS))
                + ", model_types=" + joinedLimited(issueValues(
                        issues,
                        UnifiedRuntimeRequirementReportFields.Issue.MODEL_TYPES));
    }

    private static String issueModelTypes(List<Map<?, ?>> issues) {
        List<String> modelTypes = affectedRequirements(issues).stream()
                .map(requirement -> issueText(
                        requirement,
                        UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE,
                        ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        return modelTypes.isEmpty()
                ? joinedLimited(issueValues(issues, UnifiedRuntimeRequirementReportFields.Issue.MODEL_TYPES))
                : joinedLimited(modelTypes);
    }

    private static String issueRuntimeIdsOrPairs(List<Map<?, ?>> issues) {
        List<String> runtimeIds = affectedRequirements(issues).stream()
                .flatMap(requirement -> issueValues(
                        requirement,
                        UnifiedRuntimeRequirementReportFields.Requirement.RUNTIME_IDS).stream())
                .distinct()
                .sorted()
                .toList();
        return runtimeIds.isEmpty() ? issueRequirementPairs(issues) : joinedLimited(runtimeIds);
    }

    private static String issueModalityDetails(List<Map<?, ?>> issues) {
        List<String> details = affectedRequirements(issues).stream()
                .map(UnifiedRuntimeRequirementRecommendations::issueModalityDetail)
                .distinct()
                .sorted()
                .toList();
        return details.isEmpty() ? issueRequirementPairs(issues) : joinedLimited(details);
    }

    private static String issueModalityDetail(Map<?, ?> requirement) {
        String detail = issueText(
                        requirement,
                        UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID,
                        "unknown")
                + "->" + issueText(
                        requirement,
                        UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE,
                        "unknown")
                + " needs " + joinValues(issueValues(
                        requirement,
                        UnifiedRuntimeRequirementReportFields.Requirement.REQUIRED_INPUT_MODALITIES));
        List<String> availableInputModalities = issueValues(
                requirement,
                UnifiedRuntimeRequirementReportFields.Requirement.AVAILABLE_INPUT_MODALITIES);
        if (!availableInputModalities.isEmpty()) {
            detail += " (available " + joinValues(availableInputModalities) + ")";
        }
        return detail;
    }

    private static List<Map<?, ?>> affectedRequirements(List<Map<?, ?>> issues) {
        if (issues == null) {
            return List.of();
        }
        return issues.stream()
                .flatMap(issue -> affectedRequirements(issue).stream())
                .toList();
    }

    private static List<Map<?, ?>> affectedRequirements(Map<?, ?> issue) {
        Object value = issue.get(UnifiedRuntimeRequirementReportFields.Issue.AFFECTED_REQUIREMENTS);
        if (!(value instanceof List<?> rawValues)) {
            return List.of();
        }
        List<Map<?, ?>> requirements = new ArrayList<>();
        for (Object rawValue : rawValues) {
            if (rawValue instanceof Map<?, ?> requirement) {
                requirements.add(requirement);
            }
        }
        return List.copyOf(requirements);
    }

    private static String issueText(Map<?, ?> issue, String key, String fallback) {
        Object value = issue.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static List<String> issueValues(List<Map<?, ?>> issues, String key) {
        if (issues == null) {
            return List.of();
        }
        return issues.stream()
                .flatMap(issue -> issueValues(issue, key).stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> issueValues(Map<?, ?> issue, String key) {
        Object value = issue.get(key);
        if (!(value instanceof List<?> rawValues)) {
            return List.of();
        }
        return rawValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String joinedLimited(Collection<String> values) {
        List<String> safeValues = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        if (safeValues.isEmpty()) {
            return "none";
        }
        if (safeValues.size() <= DISPLAY_LIMIT) {
            return String.join(", ", safeValues);
        }
        return String.join(", ", safeValues.subList(0, DISPLAY_LIMIT))
                + ", +" + (safeValues.size() - DISPLAY_LIMIT) + " more";
    }

    private static String joinValues(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join("/", values);
    }
}
