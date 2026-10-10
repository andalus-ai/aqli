/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Canonical mapping between requirement statuses and actionable problem codes.
 */
public enum UnifiedRuntimeRequirementIssueKind {
    MISSING_RUNTIME(
            UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
            UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
    CONFLICTING_RUNTIME(
            UnifiedRuntimeRequirementStatuses.CONFLICTING_RUNTIME,
            UnifiedRuntimeRequirementProblemCodes.CONFLICTING_MODEL_TYPE_CLAIM),
    INVALID_RUNTIME(
            UnifiedRuntimeRequirementStatuses.INVALID_RUNTIME,
            UnifiedRuntimeRequirementProblemCodes.MANIFEST_INVALID),
    INSUFFICIENT_MODALITIES(
            UnifiedRuntimeRequirementStatuses.INSUFFICIENT_MODALITIES,
            UnifiedRuntimeRequirementProblemCodes.MISSING_REQUIRED_MODALITIES),
    NOT_PRODUCTION_READY(
            UnifiedRuntimeRequirementStatuses.NOT_PRODUCTION_READY,
            UnifiedRuntimeRequirementProblemCodes.NOT_PRODUCTION_READY);

    private final String status;
    private final String problemCode;

    UnifiedRuntimeRequirementIssueKind(String status, String problemCode) {
        this.status = status;
        this.problemCode = problemCode;
    }

    public String status() {
        return status;
    }

    public String problemCode() {
        return problemCode;
    }

    public String defaultRemediationHint(
            String familyId,
            String modelType,
            List<String> requiredInputModalities,
            List<String> availableInputModalities,
            List<String> runtimeIds) {
        return switch (this) {
            case MISSING_RUNTIME -> "Attach one unified runtime plugin that claims model_type="
                    + safeValue(modelType, "unknown") + ".";
            case CONFLICTING_RUNTIME -> "Detach duplicate unified runtime plugins for model_type="
                    + safeValue(modelType, "unknown") + ".";
            case INVALID_RUNTIME -> "Fix unified runtime manifest violations for "
                    + joinedOrFallback(runtimeIds, safeValue(modelType, "unknown")) + ".";
            case INSUFFICIENT_MODALITIES -> "Attach a unified runtime for model_type="
                    + safeValue(modelType, "unknown")
                    + " with modalities: "
                    + joinedOrFallback(missingModalities(requiredInputModalities, availableInputModalities), "none")
                    + ".";
            case NOT_PRODUCTION_READY -> "Keep " + safeValue(familyId, "unknown")
                    + " out of production bundles until its unified runtime reports ready.";
        };
    }

    public static List<String> problemCodesInDeclarationOrder() {
        return Arrays.stream(values())
                .map(UnifiedRuntimeRequirementIssueKind::problemCode)
                .toList();
    }

    public static Optional<UnifiedRuntimeRequirementIssueKind> fromProblemCode(String problemCode) {
        if (problemCode == null || problemCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = problemCode.trim();
        return Arrays.stream(values())
                .filter(issueKind -> issueKind.problemCode().equals(normalized))
                .findFirst();
    }

    public static Optional<UnifiedRuntimeRequirementIssueKind> fromStatus(String status) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }
        String normalized = status.trim();
        return Arrays.stream(values())
                .filter(issueKind -> issueKind.status().equals(normalized))
                .findFirst();
    }

    public static String canonicalProblemCode(String problemCodeOrStatus) {
        if (problemCodeOrStatus == null || problemCodeOrStatus.isBlank()) {
            return "";
        }
        String normalized = problemCodeOrStatus.trim();
        return fromProblemCode(normalized)
                .or(() -> fromStatus(normalized))
                .map(UnifiedRuntimeRequirementIssueKind::problemCode)
                .orElse(normalized);
    }

    public static List<String> statusesInDeclarationOrder() {
        return Arrays.stream(values())
                .map(UnifiedRuntimeRequirementIssueKind::status)
                .toList();
    }

    private static String joinedOrFallback(List<String> values, String fallback) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        return normalized.isEmpty() ? fallback : String.join(", ", normalized);
    }

    private static List<String> missingModalities(
            List<String> requiredInputModalities,
            List<String> availableInputModalities) {
        List<String> available = availableInputModalities == null
                ? List.of()
                : availableInputModalities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        return requiredInputModalities == null
                ? List.of()
                : requiredInputModalities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .filter(value -> !available.contains(value))
                        .distinct()
                        .toList();
    }

    private static String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
