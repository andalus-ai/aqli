/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable JSON field names for unified runtime registry sections in plugin-gates CI reports.
 */
public final class UnifiedRuntimeRegistryReportFields {
    private UnifiedRuntimeRegistryReportFields() {
    }

    public static final class Section {
        public static final String RUNTIME_COUNT = "runtimeCount";
        public static final String VALID_COUNT = "validCount";
        public static final String INVALID_COUNT = "invalidCount";
        public static final String PRODUCTION_READY_COUNT = "productionReadyCount";
        public static final String MODEL_TYPES = "modelTypes";
        public static final String RUNTIMES = "runtimes";
        public static final String CONFLICTS = "conflicts";
        public static final String CONTRACT_VIOLATIONS = "contractViolations";

        private Section() {
        }
    }

    public static final class RuntimeEntry {
        public static final String RUNTIME_ID = "runtimeId";
        public static final String VALID = "valid";
        public static final String MANIFEST_AVAILABLE = "manifestAvailable";
        public static final String DIAGNOSTICS = "diagnostics";
        public static final String VIOLATIONS = "violations";
        public static final String DISPLAY_NAME = "displayName";
        public static final String MODEL_FAMILY_IDS = "modelFamilyIds";
        public static final String MODEL_TYPES = Section.MODEL_TYPES;
        public static final String INPUT_MODALITIES = "inputModalities";
        public static final String READINESS = "readiness";
        public static final String READINESS_REASON = "readinessReason";
        public static final String PRODUCTION_READY = "productionReady";

        private RuntimeEntry() {
        }
    }

    public static final class Violation {
        public static final String RUNTIME_ID = RuntimeEntry.RUNTIME_ID;
        public static final String CODE = "code";
        public static final String MESSAGE = "message";
        public static final String SUMMARY = "summary";

        private Violation() {
        }
    }

    public static List<String> registryFields() {
        return List.of(
                Section.RUNTIME_COUNT,
                Section.VALID_COUNT,
                Section.INVALID_COUNT,
                Section.PRODUCTION_READY_COUNT,
                Section.MODEL_TYPES,
                Section.RUNTIMES,
                Section.CONFLICTS,
                Section.CONTRACT_VIOLATIONS);
    }

    public static List<String> combinedSectionFields() {
        List<String> fields = new ArrayList<>(registryFields());
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION);
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA);
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS);
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS);
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS);
        fields.add(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_CONTRACT);
        return List.copyOf(fields);
    }

    public static List<String> runtimeFields() {
        return List.of(
                RuntimeEntry.RUNTIME_ID,
                RuntimeEntry.VALID,
                RuntimeEntry.MANIFEST_AVAILABLE,
                RuntimeEntry.DIAGNOSTICS,
                RuntimeEntry.VIOLATIONS,
                RuntimeEntry.DISPLAY_NAME,
                RuntimeEntry.MODEL_FAMILY_IDS,
                RuntimeEntry.MODEL_TYPES,
                RuntimeEntry.INPUT_MODALITIES,
                RuntimeEntry.READINESS,
                RuntimeEntry.READINESS_REASON,
                RuntimeEntry.PRODUCTION_READY);
    }

    public static List<String> violationFields() {
        return List.of(
                Violation.RUNTIME_ID,
                Violation.CODE,
                Violation.MESSAGE,
                Violation.SUMMARY);
    }
}
