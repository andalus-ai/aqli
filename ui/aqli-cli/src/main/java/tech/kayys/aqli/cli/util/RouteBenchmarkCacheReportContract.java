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
 * Stable contract for route benchmark cache readiness reports used by modules and plugin doctor output.
 */
public final class RouteBenchmarkCacheReportContract {
    public static final String CONTRACT_ID = "aqli.runner-route.benchmark-cache-readiness";
    public static final int SCHEMA_VERSION = 1;

    public static final String FIELD_CONTRACT_ID = "contractId";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_SCHEMA_FINGERPRINT = "schemaFingerprint";
    public static final String FIELD_REPORT_FIELDS = "reportFields";
    public static final String FIELD_REQUIRED_REPORT_FIELDS = "requiredReportFields";
    public static final String FIELD_PROFILE_TRUST_STATUSES = "profileTrustStatuses";
    public static final String FIELD_PROBLEM_CODES = "problemCodes";
    public static final String SECTION_SCHEMA = "schema";
    public static final String SECTION_SCHEMA_VALIDATION = "schemaValidation";
    public static final String SECTION_REPORT_VALIDATION = "reportValidation";

    private RouteBenchmarkCacheReportContract() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(FIELD_CONTRACT_ID, CONTRACT_ID);
        schema.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        schema.put(FIELD_SCHEMA_FINGERPRINT, schemaFingerprint());
        schema.put(FIELD_REPORT_FIELDS, reportFields());
        schema.put(FIELD_REQUIRED_REPORT_FIELDS, requiredReportFields());
        schema.put(FIELD_PROFILE_TRUST_STATUSES, profileTrustStatuses());
        schema.put(FIELD_PROBLEM_CODES, problemCodes());
        return schema;
    }

    public static Map<String, Object> schemaSection() {
        Map<String, Object> schema = schema();
        Map<String, Object> section = new LinkedHashMap<>();
        section.put(SECTION_SCHEMA, schema);
        section.put(SECTION_SCHEMA_VALIDATION, schemaValidationReport(schema));
        return section;
    }

    public static Map<String, Object> section(Map<?, ?> report) {
        Map<String, Object> section = schemaSection();
        section.put(SECTION_REPORT_VALIDATION, reportValidationReport(report));
        return section;
    }

    public static Map<String, Object> schemaValidationReport(Map<?, ?> schema) {
        return validationReport(validateSchema(schema));
    }

    public static Map<String, Object> reportValidationReport(Map<?, ?> report) {
        return validationReport(validateReport(report));
    }

    public static PluginGates applyGate(PluginGates gates, Map<?, ?> report) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> contractViolations = validateReport(report).stream()
                .map(problem -> "runner-route: benchmark cache readiness report contract failed: " + problem)
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

    public static PluginGates applySchemaGateFromSection(PluginGates gates, Map<?, ?> section) {
        if (section == null) {
            return gates == null ? PluginGates.evaluate(null, null) : gates;
        }
        return applySchemaGate(gates, asMap(section.get(SECTION_SCHEMA)));
    }

    public static PluginGates applySchemaGate(PluginGates gates, Map<?, ?> schema) {
        PluginGates base = gates == null ? PluginGates.evaluate(null, null) : gates;
        List<String> contractViolations = validateSchema(schema).stream()
                .map(problem -> "runner-route: benchmark cache readiness schema contract failed: " + problem)
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

    public static List<String> validateSchema(Map<?, ?> schema) {
        if (schema == null) {
            return List.of("route benchmark cache report schema is missing");
        }
        List<String> problems = new ArrayList<>();
        requireValue(problems, FIELD_CONTRACT_ID, schema.get(FIELD_CONTRACT_ID), CONTRACT_ID);
        requireValue(problems, FIELD_SCHEMA_VERSION, schema.get(FIELD_SCHEMA_VERSION), SCHEMA_VERSION);
        requireValue(problems, FIELD_SCHEMA_FINGERPRINT, schema.get(FIELD_SCHEMA_FINGERPRINT), schemaFingerprint());
        requireList(problems, FIELD_REPORT_FIELDS, schema.get(FIELD_REPORT_FIELDS), reportFields());
        requireList(problems, FIELD_REQUIRED_REPORT_FIELDS, schema.get(FIELD_REQUIRED_REPORT_FIELDS),
                requiredReportFields());
        requireList(problems, FIELD_PROFILE_TRUST_STATUSES, schema.get(FIELD_PROFILE_TRUST_STATUSES),
                profileTrustStatuses());
        requireList(problems, FIELD_PROBLEM_CODES, schema.get(FIELD_PROBLEM_CODES), problemCodes());
        return List.copyOf(problems);
    }

    public static List<String> validateReport(Map<?, ?> report) {
        if (report == null) {
            return List.of("route benchmark cache report is missing");
        }
        List<String> problems = new ArrayList<>();
        for (Object rawKey : report.keySet()) {
            String key = String.valueOf(rawKey);
            if (!reportFields().contains(key)) {
                problems.add("route benchmark cache report contains unknown field: " + key);
            }
        }
        for (String field : requiredReportFields()) {
            if (!report.containsKey(field)) {
                problems.add("route benchmark cache report missing required field: " + field);
            }
        }

        requireValue(problems, "schemaVersion", report.get("schemaVersion"), 1);
        requireString(problems, "status", report.get("status"));
        requireBoolean(problems, "enabled", report.get("enabled"));
        requireBoolean(problems, "healthy", report.get("healthy"));
        requireString(problems, "cacheFile", report.get("cacheFile"));
        requireBoolean(problems, "cacheFileExists", report.get("cacheFileExists"));
        requireBoolean(problems, "cacheFileReadable", report.get("cacheFileReadable"));
        requireBoolean(problems, "cacheDirectoryWritable", report.get("cacheDirectoryWritable"));
        requireNumber(problems, "staleAfterDays", report.get("staleAfterDays"));
        requireNumber(problems, "entryCount", report.get("entryCount"));
        requireNumber(problems, "staleEntryCount", report.get("staleEntryCount"));
        requireNumber(problems, "freshEntryCount", report.get("freshEntryCount"));
        requireNumber(problems, "trustedEntryCount", report.get("trustedEntryCount"));
        requireBoolean(problems, "staleProfilesAllowed", report.get("staleProfilesAllowed"));
        requireBoolean(problems, "strictHealthy", report.get("strictHealthy"));
        requireProfileTrustStatus(problems, report.get("profileTrustStatus"));
        requireProblemCodes(problems, report.get("problems"));
        requireListValue(problems, "remediationHints", report.get("remediationHints"));
        requireNumber(problems, "invalidLineCount", report.get("invalidLineCount"));
        requireListValue(problems, "providers", report.get("providers"));
        requireListValue(problems, "formats", report.get("formats"));
        requireListValue(problems, "recentEntries", report.get("recentEntries"));
        requireBoolean(problems, "failOnRouteBenchmarkCache", report.get("failOnRouteBenchmarkCache"));
        return List.copyOf(problems);
    }

    public static String schemaFingerprint() {
        String payload = String.join("\n",
                "contractId=" + CONTRACT_ID,
                "schemaVersion=" + SCHEMA_VERSION,
                "reportFields=" + String.join(",", reportFields()),
                "requiredReportFields=" + String.join(",", requiredReportFields()),
                "profileTrustStatuses=" + String.join(",", profileTrustStatuses()),
                "problemCodes=" + String.join(",", problemCodes()));
        return "sha256:" + sha256(payload);
    }

    public static List<String> reportFields() {
        return List.of(
                "schemaVersion",
                "status",
                "enabled",
                "healthy",
                "cacheFile",
                "cacheFileExists",
                "cacheFileReadable",
                "cacheDirectoryWritable",
                "staleAfterDays",
                "entryCount",
                "staleEntryCount",
                "freshEntryCount",
                "trustedEntryCount",
                "staleProfilesAllowed",
                "strictHealthy",
                "profileTrustStatus",
                "problems",
                "remediationHints",
                "invalidLineCount",
                "providers",
                "formats",
                "newestUpdatedAtEpochMs",
                "newestUpdatedAt",
                "recentEntries",
                "failOnRouteBenchmarkCache",
                "count",
                "entries");
    }

    public static List<String> requiredReportFields() {
        return List.of(
                "schemaVersion",
                "status",
                "enabled",
                "healthy",
                "cacheFile",
                "cacheFileExists",
                "cacheFileReadable",
                "cacheDirectoryWritable",
                "staleAfterDays",
                "entryCount",
                "staleEntryCount",
                "freshEntryCount",
                "trustedEntryCount",
                "staleProfilesAllowed",
                "strictHealthy",
                "profileTrustStatus",
                "problems",
                "remediationHints",
                "invalidLineCount",
                "providers",
                "formats",
                "recentEntries",
                "failOnRouteBenchmarkCache");
    }

    public static List<String> profileTrustStatuses() {
        return List.of(
                "disabled",
                "unreadable",
                "invalid",
                "unwritable",
                "empty",
                "trusted_profiles_available",
                "stale_profiles_only");
    }

    public static List<String> problemCodes() {
        return List.of(
                "cache_disabled",
                "cache_unreadable",
                "cache_directory_unwritable",
                "invalid_cache_lines",
                "stale_entries_ignored_for_route_profiles",
                "no_trusted_route_profiles");
    }

    private static Map<String, Object> validationReport(List<String> problems) {
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

    private static void requireValue(List<String> problems, String path, Object actual, Object expected) {
        if (!Objects.equals(expected, actual)) {
            problems.add(path + " expected " + expected + " but was " + actual);
        }
    }

    private static void requireString(List<String> problems, String path, Object actual) {
        if (!(actual instanceof String text) || text.isBlank()) {
            problems.add(path + " must be a non-blank string");
        }
    }

    private static void requireBoolean(List<String> problems, String path, Object actual) {
        if (!(actual instanceof Boolean)) {
            problems.add(path + " must be a boolean");
        }
    }

    private static void requireNumber(List<String> problems, String path, Object actual) {
        if (!(actual instanceof Number number)) {
            problems.add(path + " must be a number");
            return;
        }
        double value = number.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            problems.add(path + " must be a non-negative finite number");
        }
    }

    private static void requireListValue(List<String> problems, String path, Object actual) {
        if (!(actual instanceof List<?>)) {
            problems.add(path + " must be a list");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
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

    private static void requireProfileTrustStatus(List<String> problems, Object actual) {
        if (!(actual instanceof String status) || !profileTrustStatuses().contains(status)) {
            problems.add("profileTrustStatus has unknown value: " + actual);
        }
    }

    private static void requireProblemCodes(List<String> problems, Object actual) {
        if (!(actual instanceof List<?> values)) {
            problems.add("problems must be a list");
            return;
        }
        for (Object value : values) {
            String code = String.valueOf(value);
            if (!problemCodes().contains(code)) {
                problems.add("problems contains unknown code: " + code);
            }
        }
    }

    private static String gateStatus(PluginGates base) {
        if (base == null || base.passed()) {
            return "runner_route_benchmark_cache_contract_failed";
        }
        if (base.status().contains("runner_route_benchmark_cache_contract")) {
            return base.status();
        }
        return base.status() + "_and_runner_route_benchmark_cache_contract_failed";
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
                    "SHA-256 is required for route benchmark cache report fingerprints.",
                    error);
        }
    }
}
