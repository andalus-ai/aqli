/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Compatibility result between a model-family unified runtime requirement and attached runtimes.
 */
public record UnifiedRuntimeRequirementCompatibility(
        String familyId,
        String modelType,
        List<String> requiredInputModalities,
        boolean productionReadyRequired,
        String status,
        List<String> runtimeIds,
        List<String> availableInputModalities,
        List<String> problemCodes,
        List<String> remediationHints) {

    public UnifiedRuntimeRequirementCompatibility {
        familyId = familyId == null || familyId.isBlank() ? "unknown" : familyId.trim();
        modelType = modelType == null || modelType.isBlank() ? "unknown" : modelType.trim();
        requiredInputModalities = List.copyOf(requiredInputModalities == null ? List.of() : requiredInputModalities);
        status = status == null || status.isBlank()
                ? UnifiedRuntimeRequirementStatuses.UNKNOWN
                : status.trim();
        runtimeIds = List.copyOf(runtimeIds == null ? List.of() : runtimeIds);
        availableInputModalities = List.copyOf(availableInputModalities == null
                ? List.of()
                : availableInputModalities);
        problemCodes = List.copyOf(problemCodes == null ? List.of() : problemCodes);
        remediationHints = List.copyOf(remediationHints == null ? List.of() : remediationHints);
    }

    public static UnifiedRuntimeRequirementCompatibility ready(
            String familyId,
            String modelType,
            List<String> requiredInputModalities,
            boolean productionReadyRequired,
            List<String> runtimeIds,
            List<String> availableInputModalities) {
        return new UnifiedRuntimeRequirementCompatibility(
                familyId,
                modelType,
                requiredInputModalities,
                productionReadyRequired,
                UnifiedRuntimeRequirementStatuses.READY,
                runtimeIds,
                availableInputModalities,
                List.of(),
                List.of());
    }

    public static UnifiedRuntimeRequirementCompatibility attention(
            String familyId,
            String modelType,
            List<String> requiredInputModalities,
            boolean productionReadyRequired,
            UnifiedRuntimeRequirementIssueKind issueKind,
            List<String> runtimeIds,
            List<String> availableInputModalities,
            String remediationHint) {
        UnifiedRuntimeRequirementIssueKind safeIssueKind = Objects.requireNonNull(issueKind, "issueKind");
        List<String> hints = remediationHint == null || remediationHint.isBlank()
                ? List.of()
                : List.of(remediationHint.trim());
        return new UnifiedRuntimeRequirementCompatibility(
                familyId,
                modelType,
                requiredInputModalities,
                productionReadyRequired,
                safeIssueKind.status(),
                runtimeIds,
                availableInputModalities,
                List.of(safeIssueKind.problemCode()),
                hints);
    }

    public boolean compatible() {
        return problemCodes.isEmpty() && UnifiedRuntimeRequirementStatuses.READY.equals(status);
    }

    public boolean requiresAttention() {
        return !compatible();
    }

    public List<String> effectiveProblemCodes() {
        if (compatible()) {
            return List.of();
        }
        List<String> sourceCodes = problemCodes.isEmpty() ? List.of(status) : problemCodes;
        LinkedHashSet<String> effectiveCodes = new LinkedHashSet<>();
        for (String code : sourceCodes) {
            String normalized = UnifiedRuntimeRequirementIssueKind.canonicalProblemCode(code);
            if (!normalized.isBlank()) {
                effectiveCodes.add(normalized);
            }
        }
        return List.copyOf(effectiveCodes);
    }

    public List<String> effectiveRemediationHints() {
        if (compatible()) {
            return List.of();
        }
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        for (String hint : remediationHints) {
            if (hint != null && !hint.isBlank()) {
                hints.add(hint.trim());
            }
        }
        if (!hints.isEmpty()) {
            return List.copyOf(hints);
        }
        for (String problemCode : effectiveProblemCodes()) {
            UnifiedRuntimeRequirementIssueKind.fromProblemCode(problemCode)
                    .map(issueKind -> issueKind.defaultRemediationHint(
                            familyId,
                            modelType,
                            requiredInputModalities,
                            availableInputModalities,
                            runtimeIds))
                    .filter(hint -> !hint.isBlank())
                    .ifPresent(hints::add);
        }
        return List.copyOf(hints);
    }
}
