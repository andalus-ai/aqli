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
import java.util.List;

/**
 * Stable JSON field names for model-family unified runtime requirement reports.
 */
public final class UnifiedRuntimeRequirementReportFields {
    public static final String CONTRACT_ID = "aqli.model-family.unified-runtime-requirements";
    public static final int SCHEMA_VERSION = 1;

    private UnifiedRuntimeRequirementReportFields() {
    }

    public static final class Section {
        public static final String MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION =
                "modelFamilyRequirementSchemaVersion";
        public static final String MODEL_FAMILY_REQUIREMENT_SCHEMA = "modelFamilyRequirementSchema";
        public static final String MODEL_FAMILY_REQUIREMENT_CONTRACT = "modelFamilyRequirementContract";
        public static final String MODEL_FAMILY_REQUIREMENT_TOTALS = "modelFamilyRequirementTotals";
        public static final String MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS =
                "modelFamilyRequirementRecommendations";
        public static final String MODEL_FAMILY_REQUIREMENTS = "modelFamilyRequirements";

        private Section() {
        }
    }

    public static final class Contract {
        public static final String CONTRACT_ID = Schema.CONTRACT_ID;
        public static final String SCHEMA_VERSION = Schema.SCHEMA_VERSION;
        public static final String SCHEMA_FINGERPRINT = Schema.SCHEMA_FINGERPRINT;
        public static final String PASSED = "passed";
        public static final String PROBLEM_COUNT = "problemCount";
        public static final String PROBLEMS = "problems";

        private Contract() {
        }
    }

    public static final class Schema {
        public static final String CONTRACT_ID = "contractId";
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String SCHEMA_FINGERPRINT = "schemaFingerprint";
        public static final String SECTION_KEYS = "sectionKeys";
        public static final String TOTALS_KEYS = "totalsKeys";
        public static final String REQUIREMENT_KEYS = "requirementKeys";
        public static final String ISSUE_KEYS = "issueKeys";
        public static final String AFFECTED_REQUIREMENT_KEYS = "affectedRequirementKeys";

        private Schema() {
        }
    }

    public static final class Totals {
        public static final String REQUIREMENT_COUNT = "requirementCount";
        public static final String COMPATIBLE_COUNT = "compatibleCount";
        public static final String ATTENTION_COUNT = "attentionCount";
        public static final String FAMILY_IDS = "familyIds";
        public static final String COMPATIBLE_FAMILY_IDS = "compatibleFamilyIds";
        public static final String ATTENTION_FAMILY_IDS = "attentionFamilyIds";
        public static final String MODEL_TYPES = "modelTypes";
        public static final String COMPATIBLE_MODEL_TYPES = "compatibleModelTypes";
        public static final String ATTENTION_MODEL_TYPES = "attentionModelTypes";
        public static final String BY_STATUS = "byStatus";
        public static final String PROBLEM_CODES = "problemCodes";
        public static final String PROBLEM_CODE_COUNTS = "problemCodeCounts";
        public static final String REMEDIATION_HINTS = "remediationHints";
        public static final String REMEDIATION_HINT_COUNTS = "remediationHintCounts";
        public static final String ISSUES = "issues";

        private Totals() {
        }
    }

    public static final class Requirement {
        public static final String FAMILY_ID = "familyId";
        public static final String MODEL_TYPE = "modelType";
        public static final String REQUIRED_INPUT_MODALITIES = "requiredInputModalities";
        public static final String PRODUCTION_READY_REQUIRED = "productionReadyRequired";
        public static final String STATUS = "status";
        public static final String COMPATIBLE = "compatible";
        public static final String RUNTIME_IDS = "runtimeIds";
        public static final String AVAILABLE_INPUT_MODALITIES = "availableInputModalities";
        public static final String PROBLEM_CODES = Totals.PROBLEM_CODES;
        public static final String REMEDIATION_HINTS = Totals.REMEDIATION_HINTS;

        private Requirement() {
        }
    }

    public static final class Issue {
        public static final String PROBLEM_CODE = "problemCode";
        public static final String STATUS = Requirement.STATUS;
        public static final String COUNT = "count";
        public static final String AFFECTED_REQUIREMENTS = "affectedRequirements";
        public static final String FAMILY_IDS = Totals.FAMILY_IDS;
        public static final String MODEL_TYPES = Totals.MODEL_TYPES;
        public static final String REMEDIATION_HINTS = Totals.REMEDIATION_HINTS;

        private Issue() {
        }
    }

    public static List<String> sectionKeys() {
        return List.of(
                Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION,
                Section.MODEL_FAMILY_REQUIREMENT_SCHEMA,
                Section.MODEL_FAMILY_REQUIREMENT_TOTALS,
                Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS,
                Section.MODEL_FAMILY_REQUIREMENTS);
    }

    public static List<String> totalsKeys() {
        return List.of(
                Totals.REQUIREMENT_COUNT,
                Totals.COMPATIBLE_COUNT,
                Totals.ATTENTION_COUNT,
                Totals.FAMILY_IDS,
                Totals.COMPATIBLE_FAMILY_IDS,
                Totals.ATTENTION_FAMILY_IDS,
                Totals.MODEL_TYPES,
                Totals.COMPATIBLE_MODEL_TYPES,
                Totals.ATTENTION_MODEL_TYPES,
                Totals.BY_STATUS,
                Totals.PROBLEM_CODES,
                Totals.PROBLEM_CODE_COUNTS,
                Totals.REMEDIATION_HINTS,
                Totals.REMEDIATION_HINT_COUNTS,
                Totals.ISSUES);
    }

    public static List<String> requirementKeys() {
        return List.of(
                Requirement.FAMILY_ID,
                Requirement.MODEL_TYPE,
                Requirement.REQUIRED_INPUT_MODALITIES,
                Requirement.PRODUCTION_READY_REQUIRED,
                Requirement.STATUS,
                Requirement.COMPATIBLE,
                Requirement.RUNTIME_IDS,
                Requirement.AVAILABLE_INPUT_MODALITIES,
                Requirement.PROBLEM_CODES,
                Requirement.REMEDIATION_HINTS);
    }

    public static List<String> issueKeys() {
        return List.of(
                Issue.PROBLEM_CODE,
                Issue.STATUS,
                Issue.COUNT,
                Issue.AFFECTED_REQUIREMENTS,
                Issue.FAMILY_IDS,
                Issue.MODEL_TYPES,
                Issue.REMEDIATION_HINTS);
    }

    public static List<String> affectedRequirementKeys() {
        return List.of(
                Requirement.FAMILY_ID,
                Requirement.MODEL_TYPE,
                Requirement.REQUIRED_INPUT_MODALITIES,
                Requirement.RUNTIME_IDS,
                Requirement.AVAILABLE_INPUT_MODALITIES);
    }

    public static String schemaFingerprint() {
        String payload = String.join("\n",
                "contractId=" + CONTRACT_ID,
                "schemaVersion=" + SCHEMA_VERSION,
                "sectionKeys=" + String.join(",", sectionKeys()),
                "totalsKeys=" + String.join(",", totalsKeys()),
                "requirementKeys=" + String.join(",", requirementKeys()),
                "issueKeys=" + String.join(",", issueKeys()),
                "affectedRequirementKeys=" + String.join(",", affectedRequirementKeys()));
        return "sha256:" + sha256(payload);
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
            throw new IllegalStateException("SHA-256 is required for report schema fingerprints.", error);
        }
    }
}
