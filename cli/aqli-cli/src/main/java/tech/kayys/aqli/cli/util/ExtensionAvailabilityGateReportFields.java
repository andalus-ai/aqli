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
 * Stable JSON field names for extension availability gate CI reports.
 */
public final class ExtensionAvailabilityGateReportFields {
    public static final int SCHEMA_VERSION = 1;

    private ExtensionAvailabilityGateReportFields() {
    }

    public static final class Root {
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String GENERATED_AT = "generatedAt";
        public static final String EXTERNAL_PLUGIN_CLASSPATH = "externalPluginClasspath";
        public static final String EXTENSION_REGISTRY_SCOPE = "extensionRegistryScope";
        public static final String GATE = "gate";
        public static final String POLICY = "policy";
        public static final String CONTRACT = "contract";
        public static final String EXTENSIONS = "extensions";

        private Root() {
        }
    }

    public static final class Gate {
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";
        public static final String VIOLATIONS = "violations";

        private Gate() {
        }
    }

    public static final class Policy {
        public static final String CONFIGURED = "configured";
        public static final String PASSED = "passed";
        public static final String STATUS = "status";
        public static final String VIOLATIONS = "violations";
        public static final String REQUIRED_EXTENSIONS = "requiredExtensions";
        public static final String REQUIRED_PRODUCTION_EXTENSIONS = "requiredProductionExtensions";
        public static final String FORBIDDEN_EXTENSIONS = "forbiddenExtensions";

        private Policy() {
        }
    }

    public static final class Contract {
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String STATUS = "status";
        public static final String VIOLATION_COUNT = "violationCount";
        public static final String SUMMARIES = "summaries";
        public static final String VIOLATIONS = "violations";

        private Contract() {
        }
    }

    public static final class ContractViolation {
        public static final String EXTENSION_ID = "extensionId";
        public static final String CODE = "code";
        public static final String MESSAGE = "message";
        public static final String SUMMARY = "summary";

        private ContractViolation() {
        }
    }

    public static final class Extension {
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String KIND = "kind";
        public static final String STATUS = "status";
        public static final String SUMMARY = "summary";
        public static final String ATTACHED = "attached";
        public static final String DETACHED = "detached";
        public static final String HEALTHY = "healthy";
        public static final String PRODUCTION_READY = "productionReady";
        public static final String CAPABILITIES = "capabilities";
        public static final String FORMATS = "formats";
        public static final String ATTRIBUTES = "attributes";
        public static final String DIAGNOSTICS = "diagnostics";
        public static final String REMEDIATION_HINTS = "remediationHints";

        private Extension() {
        }
    }

    public static List<String> rootFields() {
        return List.of(
                Root.SCHEMA_VERSION,
                Root.GENERATED_AT,
                Root.EXTERNAL_PLUGIN_CLASSPATH,
                Root.EXTENSION_REGISTRY_SCOPE,
                Root.GATE,
                Root.POLICY,
                Root.CONTRACT,
                Root.EXTENSIONS);
    }

    public static List<String> gateFields() {
        return List.of(
                Gate.PASSED,
                Gate.FAILED,
                Gate.STATUS,
                Gate.VIOLATION_COUNT,
                Gate.VIOLATIONS);
    }

    public static List<String> policyFields() {
        return List.of(
                Policy.CONFIGURED,
                Policy.PASSED,
                Policy.STATUS,
                Policy.VIOLATIONS,
                Policy.REQUIRED_EXTENSIONS,
                Policy.REQUIRED_PRODUCTION_EXTENSIONS,
                Policy.FORBIDDEN_EXTENSIONS);
    }

    public static List<String> contractFields() {
        return List.of(
                Contract.PASSED,
                Contract.FAILED,
                Contract.STATUS,
                Contract.VIOLATION_COUNT,
                Contract.SUMMARIES,
                Contract.VIOLATIONS);
    }

    public static List<String> contractViolationFields() {
        return List.of(
                ContractViolation.EXTENSION_ID,
                ContractViolation.CODE,
                ContractViolation.MESSAGE,
                ContractViolation.SUMMARY);
    }

    public static List<String> extensionFields() {
        return List.of(
                Extension.ID,
                Extension.NAME,
                Extension.KIND,
                Extension.STATUS,
                Extension.SUMMARY,
                Extension.ATTACHED,
                Extension.DETACHED,
                Extension.HEALTHY,
                Extension.PRODUCTION_READY,
                Extension.CAPABILITIES,
                Extension.FORMATS,
                Extension.ATTRIBUTES,
                Extension.DIAGNOSTICS,
                Extension.REMEDIATION_HINTS);
    }
}
