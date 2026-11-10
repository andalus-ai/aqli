/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.List;

/**
 * Stable JSON field names for model-family contract violation reports.
 */
public final class ModelFamilyContractViolationReportFields {
    public static final String CONTRACT_ID = "model-family-contract-violation-report";
    public static final int SCHEMA_VERSION = 1;

    private ModelFamilyContractViolationReportFields() {
    }

    public static final class Report {
        public static final String CONTRACT_ID = Schema.CONTRACT_ID;
        public static final String SCHEMA_VERSION = Schema.SCHEMA_VERSION;
        public static final String SCHEMA_FINGERPRINT = Schema.SCHEMA_FINGERPRINT;
        public static final String SCHEMA = "schema";
        public static final String CATEGORY_KEYS = Schema.CATEGORY_KEYS;
        public static final String REMEDIATION_CATALOG = "remediationCatalog";
        public static final String PASSED = Validation.PASSED;
        public static final String FAILED = Validation.FAILED;
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";
        public static final String CATEGORY_COUNTS = "categoryCounts";
        public static final String CATEGORIES_WITH_VIOLATIONS = "categoriesWithViolations";
        public static final String CATEGORY_REMEDIATION_HINTS = "categoryRemediationHints";
        public static final String REMEDIATION_HINTS = "remediationHints";
        public static final String AFFECTED_FAMILY_COUNT = "affectedFamilyCount";
        public static final String AFFECTED_FAMILIES = "affectedFamilies";
        public static final String SUMMARIES = "summaries";
        public static final String VIOLATIONS = "violations";

        private Report() {
        }
    }

    public static final class Schema {
        public static final String CONTRACT_ID = "contractId";
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String SCHEMA_FINGERPRINT = "schemaFingerprint";
        public static final String REPORT_FIELDS = "reportFields";
        public static final String VIOLATION_FIELDS = "violationFields";
        public static final String CATEGORY_KEYS = "categoryKeys";

        private Schema() {
        }
    }

    public static final class Violation {
        public static final String FAMILY_ID = "familyId";
        public static final String CODE = "code";
        public static final String CATEGORY = "category";
        public static final String REMEDIATION_HINT = "remediationHint";
        public static final String MESSAGE = "message";
        public static final String SUMMARY = "summary";

        private Violation() {
        }
    }

    public static final class Validation {
        public static final String CONTRACT_ID = Schema.CONTRACT_ID;
        public static final String SCHEMA_VERSION = Schema.SCHEMA_VERSION;
        public static final String SCHEMA_FINGERPRINT = Schema.SCHEMA_FINGERPRINT;
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String PROBLEM_COUNT = "problemCount";
        public static final String PROBLEMS = "problems";

        private Validation() {
        }
    }

    public static List<String> reportFields() {
        return List.of(
                Report.CONTRACT_ID,
                Report.SCHEMA_VERSION,
                Report.SCHEMA_FINGERPRINT,
                Report.SCHEMA,
                Report.CATEGORY_KEYS,
                Report.REMEDIATION_CATALOG,
                Report.PASSED,
                Report.FAILED,
                Report.STATUS,
                Report.VIOLATION_COUNT,
                Report.CATEGORY_COUNTS,
                Report.CATEGORIES_WITH_VIOLATIONS,
                Report.CATEGORY_REMEDIATION_HINTS,
                Report.REMEDIATION_HINTS,
                Report.AFFECTED_FAMILY_COUNT,
                Report.AFFECTED_FAMILIES,
                Report.SUMMARIES,
                Report.VIOLATIONS);
    }

    public static List<String> violationFields() {
        return List.of(
                Violation.FAMILY_ID,
                Violation.CODE,
                Violation.CATEGORY,
                Violation.REMEDIATION_HINT,
                Violation.MESSAGE,
                Violation.SUMMARY);
    }
}
