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
 * Stable JSON field names for model-family bundle gate CI reports.
 */
public final class ModelFamilyBundleGateReportFields {
    public static final int SCHEMA_VERSION = 1;

    private ModelFamilyBundleGateReportFields() {
    }

    public static final class Root {
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String GENERATED_AT = "generatedAt";
        public static final String EXTERNAL_PLUGIN_CLASSPATH = "externalPluginClasspath";
        public static final String MODEL_FAMILY_REGISTRY_SCOPE = "modelFamilyRegistryScope";
        public static final String GATE = "gate";
        public static final String AVAILABILITY = "availability";
        public static final String CONTRACT = "contract";
        public static final String CONTRACT_VALIDATION = "contractValidation";
        public static final String MANIFEST = "manifest";
        public static final String DISCOVERED_FAMILIES = "discoveredFamilies";
        public static final String FAMILIES = "families";
        public static final String RUNTIME_MANIFESTS = "runtimeManifests";
        public static final String RUNTIME_COMPATIBILITY = "runtimeCompatibility";

        private Root() {
        }
    }

    public static final class Gate {
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";
        public static final String VIOLATIONS = "violations";
        public static final String CONTRACT_CATEGORY_COUNTS = "contractCategoryCounts";
        public static final String CONTRACT_REMEDIATION_HINTS = "contractRemediationHints";

        private Gate() {
        }
    }

    public static List<String> rootFields() {
        return List.of(
                Root.SCHEMA_VERSION,
                Root.GENERATED_AT,
                Root.EXTERNAL_PLUGIN_CLASSPATH,
                Root.MODEL_FAMILY_REGISTRY_SCOPE,
                Root.GATE,
                Root.AVAILABILITY,
                Root.CONTRACT,
                Root.CONTRACT_VALIDATION,
                Root.MANIFEST,
                Root.DISCOVERED_FAMILIES,
                Root.FAMILIES,
                Root.RUNTIME_MANIFESTS,
                Root.RUNTIME_COMPATIBILITY);
    }

    public static List<String> gateFields() {
        return List.of(
                Gate.PASSED,
                Gate.FAILED,
                Gate.STATUS,
                Gate.VIOLATION_COUNT,
                Gate.VIOLATIONS,
                Gate.CONTRACT_CATEGORY_COUNTS,
                Gate.CONTRACT_REMEDIATION_HINTS);
    }
}
