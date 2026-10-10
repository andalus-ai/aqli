/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Combined release gate for detachable extension policy and provider contracts.
 */
public record ExtensionAvailabilityGate(
        boolean passed,
        String status,
        int violationCount,
        List<String> violations) {

    public ExtensionAvailabilityGate {
        status = status == null || status.isBlank() ? (passed ? "passed" : "failed") : status;
        violations = List.copyOf(violations == null ? List.of() : violations);
        violationCount = Math.max(0, violationCount);
    }

    public static ExtensionAvailabilityGate evaluate(
            ExtensionAvailabilityPolicy.Result policy,
            ExtensionAvailabilityContractReport contract) {
        ExtensionAvailabilityPolicy.Result effectivePolicy = policy == null
                ? ExtensionAvailabilityPolicy.empty().evaluate(List.of())
                : policy;
        ExtensionAvailabilityContractReport effectiveContract = contract == null
                ? ExtensionAvailabilityContractReport.fromViolations(List.of())
                : contract;

        List<String> violations = new ArrayList<>();
        effectivePolicy.violations().stream()
                .map(violation -> "policy: " + violation)
                .forEach(violations::add);
        effectiveContract.summaries().stream()
                .map(summary -> "contract: " + summary)
                .forEach(violations::add);

        boolean policyPassed = effectivePolicy.passed();
        boolean contractPassed = effectiveContract.passed();
        String status = switch ((policyPassed ? 0 : 1) + (contractPassed ? 0 : 2)) {
            case 0 -> "passed";
            case 1 -> "policy_failed";
            case 2 -> "contract_failed";
            default -> "policy_and_contract_failed";
        };
        return new ExtensionAvailabilityGate(
                policyPassed && contractPassed,
                status,
                violations.size(),
                violations);
    }

    public boolean failed() {
        return !passed;
    }

    public String failureMessage() {
        String details = violations.isEmpty()
                ? ""
                : "\n  - " + String.join("\n  - ", violations);
        return "Extension availability release gate failed: " + status + details;
    }
}
