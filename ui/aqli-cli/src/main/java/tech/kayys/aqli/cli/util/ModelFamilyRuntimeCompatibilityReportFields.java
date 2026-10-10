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
 * Stable JSON field names for model-family runtime compatibility sections in CI reports.
 */
public final class ModelFamilyRuntimeCompatibilityReportFields {
    private ModelFamilyRuntimeCompatibilityReportFields() {
    }

    public static final class Compatibility {
        public static final String REQUIRES_DIRECT_SAFETENSOR_RUNTIME = "requiresDirectSafetensorRuntime";
        public static final String SELECTED_FAMILY_IDS = "selectedFamilyIds";
        public static final String SELECTED_DIRECT_SAFETENSOR_SUMMARY = "selectedDirectSafetensorSummary";
        public static final String SELECTED_DIRECT_SAFETENSOR = "selectedDirectSafetensor";
        public static final String DIRECT_SAFETENSOR_SUMMARY = "directSafetensorSummary";
        public static final String DIRECT_SAFETENSOR = "directSafetensor";

        private Compatibility() {
        }
    }

    public static final class Summary {
        public static final String RUNTIME_ID = "runtimeId";
        public static final String FAMILY_COUNT = "familyCount";
        public static final String COMPATIBLE_FAMILY_COUNT = "compatibleFamilyCount";
        public static final String BLOCKED_FAMILY_COUNT = "blockedFamilyCount";
        public static final String ATTENTION_FAMILY_COUNT = "attentionFamilyCount";
        public static final String ARCHITECTURE_ADAPTER_READY_COUNT = "architectureAdapterReadyCount";
        public static final String TOKENIZER_READY_COUNT = "tokenizerReadyCount";
        public static final String TOKENIZER_FILE_INSPECTION_AVAILABLE_COUNT =
                "tokenizerFileInspectionAvailableCount";
        public static final String COMPATIBLE_FAMILY_IDS = "compatibleFamilyIds";
        public static final String BLOCKED_FAMILY_IDS = "blockedFamilyIds";
        public static final String PROBLEM_COUNTS = "problemCounts";
        public static final String ALL_COMPATIBLE = "allCompatible";
        public static final String EMPTY = "empty";

        private Summary() {
        }
    }

    public static final class DirectSafetensorCompatibility {
        public static final String RUNTIME_ID = "runtimeId";
        public static final String COMPATIBLE = "compatible";
        public static final String REQUIRES_ATTENTION = "requiresAttention";
        public static final String SUMMARY = "summary";
        public static final String FAMILY_IDS = "familyIds";
        public static final String MODEL_TYPE = "modelType";
        public static final String ARCHITECTURE_CLASS_NAME = "architectureClassName";
        public static final String SELECTED_ARCHITECTURE_ADAPTER_ID = "selectedArchitectureAdapterId";
        public static final String SELECTED_ARCHITECTURE_ADAPTER_BY = "selectedArchitectureAdapterBy";
        public static final String ARCHITECTURE_ADAPTER_IDS = "architectureAdapterIds";
        public static final String ARCHITECTURE_ADAPTER_READY = "architectureAdapterReady";
        public static final String TOKENIZER_READY = "tokenizerReady";
        public static final String TOKENIZER_FILE_INSPECTION_AVAILABLE = "tokenizerFileInspectionAvailable";
        public static final String USABLE_TOKENIZER_IDS = "usableTokenizerIds";
        public static final String PROBLEM_CODES = "problemCodes";
        public static final String REMEDIATION_HINTS = "remediationHints";

        private DirectSafetensorCompatibility() {
        }
    }

    public static List<String> compatibilityFields() {
        return List.of(
                Compatibility.REQUIRES_DIRECT_SAFETENSOR_RUNTIME,
                Compatibility.SELECTED_FAMILY_IDS,
                Compatibility.SELECTED_DIRECT_SAFETENSOR_SUMMARY,
                Compatibility.SELECTED_DIRECT_SAFETENSOR,
                Compatibility.DIRECT_SAFETENSOR_SUMMARY,
                Compatibility.DIRECT_SAFETENSOR);
    }

    public static List<String> summaryFields() {
        return List.of(
                Summary.RUNTIME_ID,
                Summary.FAMILY_COUNT,
                Summary.COMPATIBLE_FAMILY_COUNT,
                Summary.BLOCKED_FAMILY_COUNT,
                Summary.ATTENTION_FAMILY_COUNT,
                Summary.ARCHITECTURE_ADAPTER_READY_COUNT,
                Summary.TOKENIZER_READY_COUNT,
                Summary.TOKENIZER_FILE_INSPECTION_AVAILABLE_COUNT,
                Summary.COMPATIBLE_FAMILY_IDS,
                Summary.BLOCKED_FAMILY_IDS,
                Summary.PROBLEM_COUNTS,
                Summary.ALL_COMPATIBLE,
                Summary.EMPTY);
    }

    public static List<String> directSafetensorFields() {
        return List.of(
                DirectSafetensorCompatibility.RUNTIME_ID,
                DirectSafetensorCompatibility.COMPATIBLE,
                DirectSafetensorCompatibility.REQUIRES_ATTENTION,
                DirectSafetensorCompatibility.SUMMARY,
                DirectSafetensorCompatibility.FAMILY_IDS,
                DirectSafetensorCompatibility.MODEL_TYPE,
                DirectSafetensorCompatibility.ARCHITECTURE_CLASS_NAME,
                DirectSafetensorCompatibility.SELECTED_ARCHITECTURE_ADAPTER_ID,
                DirectSafetensorCompatibility.SELECTED_ARCHITECTURE_ADAPTER_BY,
                DirectSafetensorCompatibility.ARCHITECTURE_ADAPTER_IDS,
                DirectSafetensorCompatibility.ARCHITECTURE_ADAPTER_READY,
                DirectSafetensorCompatibility.TOKENIZER_READY,
                DirectSafetensorCompatibility.TOKENIZER_FILE_INSPECTION_AVAILABLE,
                DirectSafetensorCompatibility.USABLE_TOKENIZER_IDS,
                DirectSafetensorCompatibility.PROBLEM_CODES,
                DirectSafetensorCompatibility.REMEDIATION_HINTS);
    }
}
