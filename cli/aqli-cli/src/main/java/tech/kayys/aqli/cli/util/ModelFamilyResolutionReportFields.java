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
 * Stable JSON field names for model-family resolution reports.
 */
public final class ModelFamilyResolutionReportFields {
    public static final String CONTRACT_ID = "aqli.model-family.resolution-report";
    public static final int SCHEMA_VERSION = 1;

    private ModelFamilyResolutionReportFields() {
    }

    public static final class Schema {
        public static final String CONTRACT_ID = "contractId";
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String SCHEMA_FINGERPRINT = "schemaFingerprint";
        public static final String RESOLUTION_FIELDS = "resolutionFields";
        public static final String SUPPORT_REPORT_FIELDS = "supportReportFields";
        public static final String RUNTIME_MANIFEST_FIELDS = "runtimeManifestFields";
        public static final String RUNTIME_COMPATIBILITY_FIELDS = "runtimeCompatibilityFields";
        public static final String DIRECT_SAFETENSOR_COMPATIBILITY_FIELDS =
                "directSafetensorCompatibilityFields";
        public static final String DIRECT_ARCHITECTURE_FIELDS = "directArchitectureFields";
        public static final String TOKENIZER_FIELDS = "tokenizerFields";

        private Schema() {
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

    public static final class Resolution {
        public static final String STATUS = "status";
        public static final String RESOLVED = "resolved";
        public static final String AMBIGUOUS = "ambiguous";
        public static final String MODEL_TYPE = "modelType";
        public static final String ARCHITECTURE_CLASS_NAME = "architectureClassName";
        public static final String FAMILY_IDS = "familyIds";
        public static final String SUMMARY = "summary";
        public static final String REQUIRES_ATTENTION = "requiresAttention";
        public static final String PROBLEM_CODES = "problemCodes";
        public static final String REMEDIATION_HINTS = "remediationHints";
        public static final String SUPPORT_REPORTS = "supportReports";
        public static final String RUNTIME_MANIFESTS = "runtimeManifests";
        public static final String RUNTIME_COMPATIBILITY = "runtimeCompatibility";
        public static final String DIRECT_ARCHITECTURE = "directArchitecture";
        public static final String TOKENIZERS = "tokenizers";

        private Resolution() {
        }
    }

    public static final class SupportReport {
        public static final String ID = "id";
        public static final String DISPLAY_NAME = "displayName";
        public static final String BUNDLE_PROFILE = "bundleProfile";
        public static final String CAPABILITIES = "capabilities";
        public static final String ARCHITECTURE_ADAPTER_IDS = "architectureAdapterIds";
        public static final String TOKENIZER_PROFILE_IDS = "tokenizerProfileIds";
        public static final String TOKENIZER_KINDS = "tokenizerKinds";
        public static final String DIRECT_SAFETENSOR_STATUS = "directSafetensorStatus";
        public static final String DIRECT_SAFETENSOR_REASON = "directSafetensorReason";
        public static final String DIRECT_SAFETENSOR_CAVEATS = "directSafetensorCaveats";

        private SupportReport() {
        }
    }

    public static final class RuntimeManifest {
        public static final String FAMILY_ID = "familyId";
        public static final String DISPLAY_NAME = "displayName";
        public static final String MODEL_TYPES = "modelTypes";
        public static final String ARCHITECTURE_CLASS_NAMES = "architectureClassNames";
        public static final String ARCHITECTURE_ADAPTER_IDS = "architectureAdapterIds";
        public static final String TOKENIZER_PROFILE_IDS = "tokenizerProfileIds";
        public static final String TOKENIZER_KINDS = "tokenizerKinds";
        public static final String TOKENIZER_READY = "tokenizerReady";
        public static final String CHAT_TEMPLATE_IDS = "chatTemplateIds";
        public static final String CHAT_TEMPLATE_READY = "chatTemplateReady";
        public static final String BUNDLE_PROFILE = "bundleProfile";
        public static final String CAPABILITIES = "capabilities";
        public static final String DIRECT_SAFETENSOR_STATUS = "directSafetensorStatus";
        public static final String DIRECT_SAFETENSOR_READY = "directSafetensorReady";
        public static final String DIRECT_SAFETENSOR_REASON = "directSafetensorReason";
        public static final String DIRECT_SAFETENSOR_CAVEATS = "directSafetensorCaveats";
        public static final String METADATA = "metadata";

        private RuntimeManifest() {
        }
    }

    public static final class RuntimeCompatibility {
        public static final String DIRECT_SAFETENSOR = "directSafetensor";

        private RuntimeCompatibility() {
        }
    }

    public static final class DirectSafetensorCompatibility {
        public static final String RUNTIME_ID = "runtimeId";
        public static final String COMPATIBLE = "compatible";
        public static final String REQUIRES_ATTENTION = "requiresAttention";
        public static final String SUMMARY = "summary";
        public static final String ARCHITECTURE_ADAPTER_READY = "architectureAdapterReady";
        public static final String SELECTED_ARCHITECTURE_ADAPTER_ID = "selectedArchitectureAdapterId";
        public static final String SELECTED_ARCHITECTURE_ADAPTER_BY = "selectedArchitectureAdapterBy";
        public static final String ARCHITECTURE_ADAPTER_IDS = "architectureAdapterIds";
        public static final String TOKENIZER_READY = "tokenizerReady";
        public static final String TOKENIZER_FILE_INSPECTION_AVAILABLE = "tokenizerFileInspectionAvailable";
        public static final String USABLE_TOKENIZER_IDS = "usableTokenizerIds";
        public static final String PROBLEM_CODES = "problemCodes";
        public static final String REMEDIATION_HINTS = "remediationHints";

        private DirectSafetensorCompatibility() {
        }
    }

    public static final class DirectArchitecture {
        public static final String DIRECT_SUPPORT_EXPECTED = "directSupportExpected";
        public static final String DIRECT_SUPPORT_STATUSES = "directSupportStatuses";
        public static final String ADAPTER_IDS = "adapterIds";
        public static final String SELECTED_ADAPTER_ID = "selectedAdapterId";
        public static final String SELECTED_BY = "selectedBy";
        public static final String PROBLEM_CODES = "problemCodes";

        private DirectArchitecture() {
        }
    }

    public static final class Tokenizer {
        public static final String ID = "id";
        public static final String KIND = "kind";
        public static final String REQUIRED_FILE_GROUPS = "requiredFileGroups";
        public static final String OPTIONS = "options";
        public static final String FILE_STATUS_AVAILABLE = "fileStatusAvailable";
        public static final String USABLE = "usable";
        public static final String EXISTING_FILE_GROUP = "existingFileGroup";
        public static final String MISSING_FILE_GROUPS = "missingFileGroups";

        private Tokenizer() {
        }
    }

    public static List<String> resolutionFields() {
        return List.of(
                Resolution.STATUS,
                Resolution.RESOLVED,
                Resolution.AMBIGUOUS,
                Resolution.MODEL_TYPE,
                Resolution.ARCHITECTURE_CLASS_NAME,
                Resolution.FAMILY_IDS,
                Resolution.SUMMARY,
                Resolution.REQUIRES_ATTENTION,
                Resolution.PROBLEM_CODES,
                Resolution.REMEDIATION_HINTS,
                Resolution.SUPPORT_REPORTS,
                Resolution.RUNTIME_MANIFESTS,
                Resolution.RUNTIME_COMPATIBILITY,
                Resolution.DIRECT_ARCHITECTURE,
                Resolution.TOKENIZERS);
    }

    public static List<String> supportReportFields() {
        return List.of(
                SupportReport.ID,
                SupportReport.DISPLAY_NAME,
                SupportReport.BUNDLE_PROFILE,
                SupportReport.CAPABILITIES,
                SupportReport.ARCHITECTURE_ADAPTER_IDS,
                SupportReport.TOKENIZER_PROFILE_IDS,
                SupportReport.TOKENIZER_KINDS,
                SupportReport.DIRECT_SAFETENSOR_STATUS,
                SupportReport.DIRECT_SAFETENSOR_REASON,
                SupportReport.DIRECT_SAFETENSOR_CAVEATS);
    }

    public static List<String> runtimeManifestFields() {
        return List.of(
                RuntimeManifest.FAMILY_ID,
                RuntimeManifest.DISPLAY_NAME,
                RuntimeManifest.MODEL_TYPES,
                RuntimeManifest.ARCHITECTURE_CLASS_NAMES,
                RuntimeManifest.ARCHITECTURE_ADAPTER_IDS,
                RuntimeManifest.TOKENIZER_PROFILE_IDS,
                RuntimeManifest.TOKENIZER_KINDS,
                RuntimeManifest.TOKENIZER_READY,
                RuntimeManifest.CHAT_TEMPLATE_IDS,
                RuntimeManifest.CHAT_TEMPLATE_READY,
                RuntimeManifest.BUNDLE_PROFILE,
                RuntimeManifest.CAPABILITIES,
                RuntimeManifest.DIRECT_SAFETENSOR_STATUS,
                RuntimeManifest.DIRECT_SAFETENSOR_READY,
                RuntimeManifest.DIRECT_SAFETENSOR_REASON,
                RuntimeManifest.DIRECT_SAFETENSOR_CAVEATS,
                RuntimeManifest.METADATA);
    }

    public static List<String> runtimeCompatibilityFields() {
        return List.of(RuntimeCompatibility.DIRECT_SAFETENSOR);
    }

    public static List<String> directSafetensorCompatibilityFields() {
        return List.of(
                DirectSafetensorCompatibility.RUNTIME_ID,
                DirectSafetensorCompatibility.COMPATIBLE,
                DirectSafetensorCompatibility.REQUIRES_ATTENTION,
                DirectSafetensorCompatibility.SUMMARY,
                DirectSafetensorCompatibility.ARCHITECTURE_ADAPTER_READY,
                DirectSafetensorCompatibility.SELECTED_ARCHITECTURE_ADAPTER_ID,
                DirectSafetensorCompatibility.SELECTED_ARCHITECTURE_ADAPTER_BY,
                DirectSafetensorCompatibility.ARCHITECTURE_ADAPTER_IDS,
                DirectSafetensorCompatibility.TOKENIZER_READY,
                DirectSafetensorCompatibility.TOKENIZER_FILE_INSPECTION_AVAILABLE,
                DirectSafetensorCompatibility.USABLE_TOKENIZER_IDS,
                DirectSafetensorCompatibility.PROBLEM_CODES,
                DirectSafetensorCompatibility.REMEDIATION_HINTS);
    }

    public static List<String> directArchitectureFields() {
        return List.of(
                DirectArchitecture.DIRECT_SUPPORT_EXPECTED,
                DirectArchitecture.DIRECT_SUPPORT_STATUSES,
                DirectArchitecture.ADAPTER_IDS,
                DirectArchitecture.SELECTED_ADAPTER_ID,
                DirectArchitecture.SELECTED_BY,
                DirectArchitecture.PROBLEM_CODES);
    }

    public static List<String> tokenizerFields() {
        return List.of(
                Tokenizer.ID,
                Tokenizer.KIND,
                Tokenizer.REQUIRED_FILE_GROUPS,
                Tokenizer.OPTIONS,
                Tokenizer.FILE_STATUS_AVAILABLE,
                Tokenizer.USABLE,
                Tokenizer.EXISTING_FILE_GROUP,
                Tokenizer.MISSING_FILE_GROUPS);
    }

    public static String schemaFingerprint() {
        String payload = String.join("\n",
                "contractId=" + CONTRACT_ID,
                "schemaVersion=" + SCHEMA_VERSION,
                "resolutionFields=" + String.join(",", resolutionFields()),
                "supportReportFields=" + String.join(",", supportReportFields()),
                "runtimeManifestFields=" + String.join(",", runtimeManifestFields()),
                "runtimeCompatibilityFields=" + String.join(",", runtimeCompatibilityFields()),
                "directSafetensorCompatibilityFields="
                        + String.join(",", directSafetensorCompatibilityFields()),
                "directArchitectureFields=" + String.join(",", directArchitectureFields()),
                "tokenizerFields=" + String.join(",", tokenizerFields()));
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
