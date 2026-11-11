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
 * Stable JSON field names for the combined plugin gates CI report.
 */
public final class PluginGatesReportFields {
    public static final int SCHEMA_VERSION = 3;

    private PluginGatesReportFields() {
    }

    public static final class Root {
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String GENERATED_AT = "generatedAt";
        public static final String EXTERNAL_PLUGIN_CLASSPATH = "externalPluginClasspath";
        public static final String GATE = "gate";
        public static final String RUNNER_ROUTE_CONTRACTS = "runnerRouteContracts";
        public static final String ROUTE_BENCHMARK_CACHE_REPORT_CONTRACT = "routeBenchmarkCacheReportContract";
        public static final String PLUGIN_DIRECTORY_READINESS = "pluginDirectoryReadiness";
        public static final String UNIFIED_RUNTIMES = "unifiedRuntimes";
        public static final String EXTENSION_AVAILABILITY = "extensionAvailability";
        public static final String MODEL_FAMILY_BUNDLE = "modelFamilyBundle";

        private Root() {
        }
    }

    public static final class Gate {
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";
        public static final String VIOLATIONS = "violations";
        public static final String VIOLATION_CATEGORIES = "violationCategories";
        public static final String EXTENSION_STATUS = "extensionStatus";
        public static final String MODEL_FAMILY_STATUS = "modelFamilyStatus";
        public static final String EXTENSION_VIOLATION_COUNT = "extensionViolationCount";
        public static final String MODEL_FAMILY_VIOLATION_COUNT = "modelFamilyViolationCount";
        public static final String MODEL_FAMILY_CONTRACT_CATEGORY_COUNTS = "modelFamilyContractCategoryCounts";
        public static final String MODEL_FAMILY_CONTRACT_REMEDIATION_HINTS =
                "modelFamilyContractRemediationHints";

        private Gate() {
        }
    }

    public static List<String> rootFields() {
        return List.of(
                Root.SCHEMA_VERSION,
                Root.GENERATED_AT,
                Root.EXTERNAL_PLUGIN_CLASSPATH,
                Root.GATE,
                Root.RUNNER_ROUTE_CONTRACTS,
                Root.ROUTE_BENCHMARK_CACHE_REPORT_CONTRACT,
                Root.PLUGIN_DIRECTORY_READINESS,
                Root.UNIFIED_RUNTIMES,
                Root.EXTENSION_AVAILABILITY,
                Root.MODEL_FAMILY_BUNDLE);
    }

    public static List<String> gateFields() {
        return List.of(
                Gate.PASSED,
                Gate.FAILED,
                Gate.STATUS,
                Gate.VIOLATION_COUNT,
                Gate.VIOLATIONS,
                Gate.VIOLATION_CATEGORIES,
                Gate.EXTENSION_STATUS,
                Gate.MODEL_FAMILY_STATUS,
                Gate.EXTENSION_VIOLATION_COUNT,
                Gate.MODEL_FAMILY_VIOLATION_COUNT,
                Gate.MODEL_FAMILY_CONTRACT_CATEGORY_COUNTS,
                Gate.MODEL_FAMILY_CONTRACT_REMEDIATION_HINTS);
    }
}
