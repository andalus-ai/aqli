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
 * Stable JSON field names for model-family inventory sections in bundle CI reports.
 */
public final class ModelFamilyBundleInventoryReportFields {
    private ModelFamilyBundleInventoryReportFields() {
    }

    public static final class Family {
        public static final String ID = "id";
        public static final String DISPLAY_NAME = "displayName";
        public static final String BUNDLE_PROFILE = "bundleProfile";
        public static final String DEFAULT_BUNDLE = "defaultBundle";
        public static final String MODEL_TYPES = "modelTypes";
        public static final String ARCHITECTURE_CLASS_NAMES = "architectureClassNames";
        public static final String ARCHITECTURE_ADAPTER_IDS = "architectureAdapterIds";
        public static final String TOKENIZER_PROFILE_IDS = "tokenizerProfileIds";
        public static final String TOKENIZER_KINDS = "tokenizerKinds";
        public static final String CAPABILITIES = "capabilities";
        public static final String DIRECT_SAFETENSOR_STATUS = "directSafetensorStatus";
        public static final String DIRECT_SAFETENSOR_READY = "directSafetensorReady";
        public static final String DIRECT_SAFETENSOR_REASON = "directSafetensorReason";
        public static final String DIRECT_SAFETENSOR_CAVEATS = "directSafetensorCaveats";
        public static final String METADATA = "metadata";

        private Family() {
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
        public static final String UNIFIED_RUNTIME_REQUIREMENTS = "unifiedRuntimeRequirements";
        public static final String METADATA = "metadata";

        private RuntimeManifest() {
        }
    }

    public static final class RuntimeRequirement {
        public static final String MODEL_TYPE = UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE;
        public static final String REQUIRED_INPUT_MODALITIES =
                UnifiedRuntimeRequirementReportFields.Requirement.REQUIRED_INPUT_MODALITIES;
        public static final String PRODUCTION_READY_REQUIRED =
                UnifiedRuntimeRequirementReportFields.Requirement.PRODUCTION_READY_REQUIRED;
        public static final String REASON = "reason";
        public static final String METADATA = "metadata";

        private RuntimeRequirement() {
        }
    }

    public static List<String> familyFields() {
        return List.of(
                Family.ID,
                Family.DISPLAY_NAME,
                Family.BUNDLE_PROFILE,
                Family.DEFAULT_BUNDLE,
                Family.MODEL_TYPES,
                Family.ARCHITECTURE_CLASS_NAMES,
                Family.ARCHITECTURE_ADAPTER_IDS,
                Family.TOKENIZER_PROFILE_IDS,
                Family.TOKENIZER_KINDS,
                Family.CAPABILITIES,
                Family.DIRECT_SAFETENSOR_STATUS,
                Family.DIRECT_SAFETENSOR_READY,
                Family.DIRECT_SAFETENSOR_REASON,
                Family.DIRECT_SAFETENSOR_CAVEATS,
                Family.METADATA);
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
                RuntimeManifest.UNIFIED_RUNTIME_REQUIREMENTS,
                RuntimeManifest.METADATA);
    }

    public static List<String> runtimeRequirementFields() {
        return List.of(
                RuntimeRequirement.MODEL_TYPE,
                RuntimeRequirement.REQUIRED_INPUT_MODALITIES,
                RuntimeRequirement.PRODUCTION_READY_REQUIRED,
                RuntimeRequirement.REASON,
                RuntimeRequirement.METADATA);
    }
}
