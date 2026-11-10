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

/**
 * Lightweight validator for the model-family unified runtime requirement report contract.
 */
public final class UnifiedRuntimeRequirementReportContract {
    private UnifiedRuntimeRequirementReportContract() {
    }

    public static Map<String, Object> report(Map<?, ?> section) {
        List<String> problems = validateSection(section);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
                UnifiedRuntimeRequirementReportFields.Contract.CONTRACT_ID,
                UnifiedRuntimeRequirementReportFields.CONTRACT_ID);
        report.put(
                UnifiedRuntimeRequirementReportFields.Contract.SCHEMA_VERSION,
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION);
        report.put(
                UnifiedRuntimeRequirementReportFields.Contract.SCHEMA_FINGERPRINT,
                UnifiedRuntimeRequirementReportFields.schemaFingerprint());
        report.put(UnifiedRuntimeRequirementReportFields.Contract.PASSED, problems.isEmpty());
        report.put(UnifiedRuntimeRequirementReportFields.Contract.PROBLEM_COUNT, problems.size());
        report.put(UnifiedRuntimeRequirementReportFields.Contract.PROBLEMS, problems);
        return report;
    }

    public static List<String> validateSection(Map<?, ?> section) {
        if (section == null) {
            return List.of("model-family unified runtime requirement section is missing");
        }
        List<String> problems = new ArrayList<>();
        requireValue(
                problems,
                "section." + UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION,
                section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION),
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION);

        Object schema = section.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA);
        if (schema instanceof Map<?, ?> schemaReport) {
            problems.addAll(validateSchema(schemaReport));
        } else {
            problems.add("section."
                    + UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA
                    + " must be an object");
        }

        requireType(
                problems,
                "section." + UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS,
                section.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS),
                Map.class,
                "object");
        requireType(
                problems,
                "section." + UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS,
                section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS),
                List.class,
                "list");
        requireType(
                problems,
                "section." + UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS,
                section.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS),
                List.class,
                "list");
        return List.copyOf(problems);
    }

    public static List<String> validateSchema(Map<?, ?> schema) {
        if (schema == null) {
            return List.of("model-family unified runtime requirement schema is missing");
        }
        List<String> problems = new ArrayList<>();
        requireValue(
                problems,
                "schema." + UnifiedRuntimeRequirementReportFields.Schema.CONTRACT_ID,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.CONTRACT_ID),
                UnifiedRuntimeRequirementReportFields.CONTRACT_ID);
        requireValue(
                problems,
                "schema." + UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_VERSION,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_VERSION),
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION);
        requireValue(
                problems,
                "schema." + UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_FINGERPRINT,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_FINGERPRINT),
                UnifiedRuntimeRequirementReportFields.schemaFingerprint());
        requireList(
                problems,
                UnifiedRuntimeRequirementReportFields.Schema.SECTION_KEYS,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SECTION_KEYS),
                UnifiedRuntimeRequirementReportFields.sectionKeys());
        requireList(
                problems,
                UnifiedRuntimeRequirementReportFields.Schema.TOTALS_KEYS,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.TOTALS_KEYS),
                UnifiedRuntimeRequirementReportFields.totalsKeys());
        requireList(
                problems,
                UnifiedRuntimeRequirementReportFields.Schema.REQUIREMENT_KEYS,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.REQUIREMENT_KEYS),
                UnifiedRuntimeRequirementReportFields.requirementKeys());
        requireList(
                problems,
                UnifiedRuntimeRequirementReportFields.Schema.ISSUE_KEYS,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.ISSUE_KEYS),
                UnifiedRuntimeRequirementReportFields.issueKeys());
        requireList(
                problems,
                UnifiedRuntimeRequirementReportFields.Schema.AFFECTED_REQUIREMENT_KEYS,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.AFFECTED_REQUIREMENT_KEYS),
                UnifiedRuntimeRequirementReportFields.affectedRequirementKeys());
        return List.copyOf(problems);
    }

    private static void requireValue(
            List<String> problems,
            String path,
            Object actual,
            Object expected) {
        if (!Objects.equals(expected, actual)) {
            problems.add(path + " expected " + expected + " but was " + actual);
        }
    }

    private static void requireList(
            List<String> problems,
            String field,
            Object actual,
            List<String> expected) {
        String path = "schema." + field;
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

    private static void requireType(
            List<String> problems,
            String path,
            Object actual,
            Class<?> expectedType,
            String expectedName) {
        if (!expectedType.isInstance(actual)) {
            problems.add(path + " must be a " + expectedName);
        }
    }
}
