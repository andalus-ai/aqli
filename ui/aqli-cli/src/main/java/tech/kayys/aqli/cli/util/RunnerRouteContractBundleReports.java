/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract bundle for runner-route JSON surfaces shared by commands and CI gate artifacts.
 */
public final class RunnerRouteContractBundleReports {
    public static final String CONTRACT_ID = "aqli.runner-route.contract-bundle";
    public static final int SCHEMA_VERSION = 1;

    public static final String FIELD_CONTRACT_ID = "contractId";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_SCHEMA_FINGERPRINT = "schemaFingerprint";
    public static final String FIELD_CONTRACT_IDS = "contractIds";
    public static final String FIELD_PAYLOAD_ROOTS = "payloadRoots";
    public static final String FIELD_VALIDATION_ROOTS = "validationRoots";
    public static final String FIELD_CONTRACTS = "contracts";

    public static final String CONTRACT_ROLE = "role";
    public static final String CONTRACT_SCHEMA_VERSION = "schemaVersion";
    public static final String CONTRACT_SCHEMA_FINGERPRINT = "schemaFingerprint";
    public static final String CONTRACT_PAYLOAD_ROOTS = "payloadRoots";
    public static final String CONTRACT_VALIDATION_ROOTS = "validationRoots";

    public static final String SECTION_BUNDLE = "bundle";
    public static final String SECTION_VALIDATION = "validation";

    private RunnerRouteContractBundleReports() {
    }

    public static Map<String, Object> section() {
        Map<String, Object> bundle = report();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put(SECTION_BUNDLE, bundle);
        section.put(SECTION_VALIDATION, validationReport(bundle));
        return section;
    }

    public static Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(FIELD_CONTRACT_ID, CONTRACT_ID);
        report.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        report.put(FIELD_SCHEMA_FINGERPRINT, schemaFingerprint());
        report.put(FIELD_CONTRACT_IDS, contractIds());
        report.put(FIELD_PAYLOAD_ROOTS, payloadRoots());
        report.put(FIELD_VALIDATION_ROOTS, validationRoots());
        report.put(FIELD_CONTRACTS, contracts());
        return report;
    }

    public static Map<String, Object> validationReport(Map<?, ?> report) {
        List<String> problems = validateReport(report);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put(FIELD_CONTRACT_ID, CONTRACT_ID);
        validation.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        validation.put(FIELD_SCHEMA_FINGERPRINT, schemaFingerprint());
        validation.put("passed", problems.isEmpty());
        validation.put("failed", !problems.isEmpty());
        validation.put("problemCount", problems.size());
        validation.put("problems", problems);
        return validation;
    }

    public static PluginGates applyGate(PluginGates gates, Map<?, ?> report) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> contractViolations = validateReport(report).stream()
                .map(problem -> "runner-route: contract bundle failed: " + problem)
                .toList();
        if (contractViolations.isEmpty()) {
            return base;
        }
        List<String> violations = new ArrayList<>(base.violations());
        violations.addAll(contractViolations);
        return new PluginGates(
                false,
                gateStatus(base),
                violations.size(),
                violations,
                base.extensionStatus(),
                base.modelFamilyStatus(),
                base.extensionViolationCount(),
                base.modelFamilyViolationCount(),
                base.modelFamilyContractCategoryCounts(),
                base.modelFamilyContractRemediationHints());
    }

    public static PluginGates applyGateFromSection(PluginGates gates, Map<?, ?> section) {
        if (section == null) {
            return gates == null ? PluginGates.evaluate(null, null) : gates;
        }
        return applyGate(gates, asMap(section.get(SECTION_BUNDLE)));
    }

    public static List<String> validateReport(Map<?, ?> report) {
        if (report == null) {
            return List.of("runner route contract bundle is missing");
        }
        List<String> problems = new ArrayList<>();
        requireValue(problems, FIELD_CONTRACT_ID, report.get(FIELD_CONTRACT_ID), CONTRACT_ID);
        requireValue(problems, FIELD_SCHEMA_VERSION, report.get(FIELD_SCHEMA_VERSION), SCHEMA_VERSION);
        requireValue(problems, FIELD_SCHEMA_FINGERPRINT, report.get(FIELD_SCHEMA_FINGERPRINT), schemaFingerprint());
        requireList(problems, FIELD_CONTRACT_IDS, report.get(FIELD_CONTRACT_IDS), contractIds());
        requireList(problems, FIELD_PAYLOAD_ROOTS, report.get(FIELD_PAYLOAD_ROOTS), payloadRoots());
        requireList(problems, FIELD_VALIDATION_ROOTS, report.get(FIELD_VALIDATION_ROOTS), validationRoots());
        requireContracts(problems, report.get(FIELD_CONTRACTS));
        return List.copyOf(problems);
    }

    public static String schemaFingerprint() {
        String payload = String.join("\n",
                "contractId=" + CONTRACT_ID,
                "schemaVersion=" + SCHEMA_VERSION,
                "contractIds=" + String.join(",", contractIds()),
                "payloadRoots=" + String.join(",", payloadRoots()),
                "validationRoots=" + String.join(",", validationRoots()),
                "contracts=" + contracts());
        return "sha256:" + sha256(payload);
    }

    private static List<String> contractIds() {
        return List.of(
                RunnerRouteReportFields.CONTRACT_ID,
                RouteReportPayloadFields.CONTRACT_ID,
                RoutePreflightDiagnosticFields.CONTRACT_ID,
                RunnerRoutePolicyFields.CONTRACT_ID,
                RouteBenchmarkCacheReportContract.CONTRACT_ID);
    }

    private static List<String> payloadRoots() {
        return List.of(
                RunnerRouteReportFields.METADATA_ROOT,
                RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEMS,
                RouteReportPayloadFields.Payload.NEXT_ACTIONS,
                "selectionPolicy",
                "routeBenchmarkCache");
    }

    private static List<String> validationRoots() {
        return List.of(
                RunnerRouteReportFields.VALIDATION_METADATA_ROOT,
                RoutePreflightDiagnosticFields.VALIDATION_ROOT,
                RouteReportPayloadFields.VALIDATION_ROOT,
                "routeBenchmarkCacheReportValidation");
    }

    private static String gateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "runner_route_contract_bundle_failed";
        }
        if (base.status().contains("runner_route_contract_bundle")) {
            return base.status();
        }
        return base.status() + "_and_runner_route_contract_bundle_failed";
    }

    private static List<Map<String, Object>> contracts() {
        return List.of(
                contract(
                        "runner_route_report",
                        RunnerRouteReportFields.CONTRACT_ID,
                        RunnerRouteReportFields.SCHEMA_VERSION,
                        RunnerRouteReportFields.schemaFingerprint(),
                        List.of(RunnerRouteReportFields.METADATA_ROOT),
                        List.of(RunnerRouteReportFields.VALIDATION_METADATA_ROOT)),
                contract(
                        "route_report_payload",
                        RouteReportPayloadFields.CONTRACT_ID,
                        RouteReportPayloadFields.SCHEMA_VERSION,
                        RouteReportPayloadFields.schemaFingerprint(),
                        List.of("$"),
                        List.of(RouteReportPayloadFields.VALIDATION_ROOT)),
                contract(
                        "route_preflight_diagnostics",
                        RoutePreflightDiagnosticFields.CONTRACT_ID,
                        RoutePreflightDiagnosticFields.SCHEMA_VERSION,
                        RoutePreflightDiagnosticFields.schemaFingerprint(),
                        List.of(
                                RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEMS,
                                RouteReportPayloadFields.Payload.NEXT_ACTIONS),
                        List.of(RoutePreflightDiagnosticFields.VALIDATION_ROOT)),
                contract(
                        "runner_selection_policy",
                        RunnerRoutePolicyFields.CONTRACT_ID,
                        RunnerRoutePolicyFields.SCHEMA_VERSION,
                        RunnerRoutePolicyFields.schemaFingerprint(),
                        List.of("selectionPolicy"),
                        List.of()),
                contract(
                        "route_benchmark_cache_readiness",
                        RouteBenchmarkCacheReportContract.CONTRACT_ID,
                        RouteBenchmarkCacheReportContract.SCHEMA_VERSION,
                        RouteBenchmarkCacheReportContract.schemaFingerprint(),
                        List.of("routeBenchmarkCache"),
                        List.of("routeBenchmarkCacheReportValidation")));
    }

    private static Map<String, Object> contract(
            String role,
            String contractId,
            int schemaVersion,
            String schemaFingerprint,
            List<String> payloadRoots,
            List<String> validationRoots) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put(FIELD_CONTRACT_ID, contractId);
        contract.put(CONTRACT_ROLE, role);
        contract.put(CONTRACT_SCHEMA_VERSION, schemaVersion);
        contract.put(CONTRACT_SCHEMA_FINGERPRINT, schemaFingerprint);
        contract.put(CONTRACT_PAYLOAD_ROOTS, payloadRoots);
        contract.put(CONTRACT_VALIDATION_ROOTS, validationRoots);
        return contract;
    }

    private static void requireContracts(List<String> problems, Object actual) {
        if (!(actual instanceof List<?> actualContracts)) {
            problems.add(FIELD_CONTRACTS + " must be a list");
            return;
        }
        if (!Objects.equals(contracts(), actualContracts)) {
            problems.add(FIELD_CONTRACTS + " expected " + contracts() + " but was " + actualContracts);
        }
    }

    private static void requireValue(List<String> problems, String path, Object actual, Object expected) {
        if (!Objects.equals(expected, actual)) {
            problems.add(path + " expected " + expected + " but was " + actual);
        }
    }

    private static void requireList(List<String> problems, String path, Object actual, List<String> expected) {
        if (!(actual instanceof List<?> values)) {
            problems.add(path + " must be a list");
            return;
        }
        List<String> normalized = values.stream()
                .map(String::valueOf)
                .toList();
        if (!Objects.equals(expected, normalized)) {
            problems.add(path + " expected " + expected + " but was " + normalized);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return (Map<?, ?>) value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte raw : digest) {
                int valueByte = raw & 0xff;
                if (valueByte < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256 is required for runner route contract bundle fingerprints.",
                    error);
        }
    }
}
