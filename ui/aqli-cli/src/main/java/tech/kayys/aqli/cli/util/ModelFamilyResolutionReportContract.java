/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.ModelFamilyResolutionReportFields.DirectArchitecture;
import tech.kayys.aqli.cli.util.ModelFamilyResolutionReportFields.Resolution;
import tech.kayys.aqli.cli.util.ModelFamilyResolutionReportFields.RuntimeCompatibility;
import tech.kayys.aqli.cli.util.ModelFamilyResolutionReportFields.Schema;
import tech.kayys.aqli.cli.util.ModelFamilyResolutionReportFields.Validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight validator for reusable model-family resolution report maps.
 */
public final class ModelFamilyResolutionReportContract {
    private ModelFamilyResolutionReportContract() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(Schema.CONTRACT_ID, ModelFamilyResolutionReportFields.CONTRACT_ID);
        schema.put(Schema.SCHEMA_VERSION, ModelFamilyResolutionReportFields.SCHEMA_VERSION);
        schema.put(Schema.SCHEMA_FINGERPRINT, ModelFamilyResolutionReportFields.schemaFingerprint());
        schema.put(Schema.RESOLUTION_FIELDS, ModelFamilyResolutionReportFields.resolutionFields());
        schema.put(Schema.SUPPORT_REPORT_FIELDS, ModelFamilyResolutionReportFields.supportReportFields());
        schema.put(Schema.RUNTIME_MANIFEST_FIELDS, ModelFamilyResolutionReportFields.runtimeManifestFields());
        schema.put(Schema.RUNTIME_COMPATIBILITY_FIELDS,
                ModelFamilyResolutionReportFields.runtimeCompatibilityFields());
        schema.put(Schema.DIRECT_SAFETENSOR_COMPATIBILITY_FIELDS,
                ModelFamilyResolutionReportFields.directSafetensorCompatibilityFields());
        schema.put(Schema.DIRECT_ARCHITECTURE_FIELDS,
                ModelFamilyResolutionReportFields.directArchitectureFields());
        schema.put(Schema.TOKENIZER_FIELDS, ModelFamilyResolutionReportFields.tokenizerFields());
        return schema;
    }

    public static List<String> validateSchema(Map<?, ?> schema) {
        if (schema == null) {
            return List.of("model-family resolution report schema is missing");
        }
        List<String> problems = new ArrayList<>();
        requireValue(problems, "schema." + Schema.CONTRACT_ID,
                schema.get(Schema.CONTRACT_ID), ModelFamilyResolutionReportFields.CONTRACT_ID);
        requireValue(problems, "schema." + Schema.SCHEMA_VERSION,
                schema.get(Schema.SCHEMA_VERSION), ModelFamilyResolutionReportFields.SCHEMA_VERSION);
        requireValue(problems, "schema." + Schema.SCHEMA_FINGERPRINT,
                schema.get(Schema.SCHEMA_FINGERPRINT), ModelFamilyResolutionReportFields.schemaFingerprint());
        requireList(problems, Schema.RESOLUTION_FIELDS,
                schema.get(Schema.RESOLUTION_FIELDS), ModelFamilyResolutionReportFields.resolutionFields());
        requireList(problems, Schema.SUPPORT_REPORT_FIELDS,
                schema.get(Schema.SUPPORT_REPORT_FIELDS), ModelFamilyResolutionReportFields.supportReportFields());
        requireList(problems, Schema.RUNTIME_MANIFEST_FIELDS,
                schema.get(Schema.RUNTIME_MANIFEST_FIELDS), ModelFamilyResolutionReportFields.runtimeManifestFields());
        requireList(problems, Schema.RUNTIME_COMPATIBILITY_FIELDS,
                schema.get(Schema.RUNTIME_COMPATIBILITY_FIELDS),
                ModelFamilyResolutionReportFields.runtimeCompatibilityFields());
        requireList(problems, Schema.DIRECT_SAFETENSOR_COMPATIBILITY_FIELDS,
                schema.get(Schema.DIRECT_SAFETENSOR_COMPATIBILITY_FIELDS),
                ModelFamilyResolutionReportFields.directSafetensorCompatibilityFields());
        requireList(problems, Schema.DIRECT_ARCHITECTURE_FIELDS,
                schema.get(Schema.DIRECT_ARCHITECTURE_FIELDS),
                ModelFamilyResolutionReportFields.directArchitectureFields());
        requireList(problems, Schema.TOKENIZER_FIELDS,
                schema.get(Schema.TOKENIZER_FIELDS), ModelFamilyResolutionReportFields.tokenizerFields());
        return List.copyOf(problems);
    }

    public static Map<String, Object> schemaValidationReport(Map<?, ?> schema) {
        List<String> problems = validateSchema(schema);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put(Validation.CONTRACT_ID, ModelFamilyResolutionReportFields.CONTRACT_ID);
        validation.put(Validation.SCHEMA_VERSION, ModelFamilyResolutionReportFields.SCHEMA_VERSION);
        validation.put(Validation.SCHEMA_FINGERPRINT, ModelFamilyResolutionReportFields.schemaFingerprint());
        validation.put(Validation.PASSED, problems.isEmpty());
        validation.put(Validation.FAILED, !problems.isEmpty());
        validation.put(Validation.PROBLEM_COUNT, problems.size());
        validation.put(Validation.PROBLEMS, problems);
        return validation;
    }

    public static List<String> validateReport(Map<?, ?> report) {
        if (report == null) {
            return List.of("model-family resolution report is missing");
        }
        List<String> problems = new ArrayList<>();
        requireKeys(problems, "resolution", report, ModelFamilyResolutionReportFields.resolutionFields());

        requireListOfMaps(problems, "resolution." + Resolution.SUPPORT_REPORTS,
                report.get(Resolution.SUPPORT_REPORTS),
                ModelFamilyResolutionReportFields.supportReportFields());
        requireListOfMaps(problems, "resolution." + Resolution.RUNTIME_MANIFESTS,
                report.get(Resolution.RUNTIME_MANIFESTS),
                ModelFamilyResolutionReportFields.runtimeManifestFields());

        Map<?, ?> runtimeCompatibility = mapValue(report.get(Resolution.RUNTIME_COMPATIBILITY));
        if (runtimeCompatibility == null) {
            problems.add("resolution." + Resolution.RUNTIME_COMPATIBILITY + " must be an object");
        } else {
            requireKeys(problems, "resolution." + Resolution.RUNTIME_COMPATIBILITY,
                    runtimeCompatibility, ModelFamilyResolutionReportFields.runtimeCompatibilityFields());
            Map<?, ?> directSafetensor = mapValue(runtimeCompatibility.get(RuntimeCompatibility.DIRECT_SAFETENSOR));
            if (directSafetensor == null) {
                problems.add("resolution." + Resolution.RUNTIME_COMPATIBILITY
                        + "." + RuntimeCompatibility.DIRECT_SAFETENSOR + " must be an object");
            } else {
                requireKeys(problems, "resolution." + Resolution.RUNTIME_COMPATIBILITY
                                + "." + RuntimeCompatibility.DIRECT_SAFETENSOR,
                        directSafetensor,
                        ModelFamilyResolutionReportFields.directSafetensorCompatibilityFields());
            }
        }

        Map<?, ?> directArchitecture = mapValue(report.get(Resolution.DIRECT_ARCHITECTURE));
        if (directArchitecture == null) {
            problems.add("resolution." + Resolution.DIRECT_ARCHITECTURE + " must be an object");
        } else {
            requireKeys(problems, "resolution." + Resolution.DIRECT_ARCHITECTURE,
                    directArchitecture, ModelFamilyResolutionReportFields.directArchitectureFields());
            requireListValue(problems, "resolution." + Resolution.DIRECT_ARCHITECTURE
                    + "." + DirectArchitecture.ADAPTER_IDS, directArchitecture.get(DirectArchitecture.ADAPTER_IDS));
        }

        requireListOfMaps(problems, "resolution." + Resolution.TOKENIZERS,
                report.get(Resolution.TOKENIZERS), ModelFamilyResolutionReportFields.tokenizerFields());
        return List.copyOf(problems);
    }

    public static Map<String, Object> validationReport(Map<?, ?> report) {
        List<String> problems = validateReport(report);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put(Validation.CONTRACT_ID, ModelFamilyResolutionReportFields.CONTRACT_ID);
        validation.put(Validation.SCHEMA_VERSION, ModelFamilyResolutionReportFields.SCHEMA_VERSION);
        validation.put(Validation.SCHEMA_FINGERPRINT, ModelFamilyResolutionReportFields.schemaFingerprint());
        validation.put(Validation.PASSED, problems.isEmpty());
        validation.put(Validation.FAILED, !problems.isEmpty());
        validation.put(Validation.PROBLEM_COUNT, problems.size());
        validation.put(Validation.PROBLEMS, problems);
        return validation;
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

    private static void requireKeys(
            List<String> problems,
            String path,
            Map<?, ?> report,
            List<String> expectedKeys) {
        List<String> actualKeys = report.keySet().stream()
                .map(String::valueOf)
                .toList();
        if (!Objects.equals(expectedKeys, actualKeys)) {
            problems.add(path + " fields expected " + expectedKeys + " but was " + actualKeys);
        }
    }

    private static void requireListOfMaps(
            List<String> problems,
            String path,
            Object actual,
            List<String> expectedFields) {
        if (!(actual instanceof List<?> rows)) {
            problems.add(path + " must be a list");
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            Map<?, ?> row = mapValue(rows.get(index));
            if (row == null) {
                problems.add(path + "[" + index + "] must be an object");
            } else {
                requireKeys(problems, path + "[" + index + "]", row, expectedFields);
            }
        }
    }

    private static void requireListValue(List<String> problems, String path, Object actual) {
        if (!(actual instanceof List<?>)) {
            problems.add(path + " must be a list");
        }
    }

    private static Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }
}
