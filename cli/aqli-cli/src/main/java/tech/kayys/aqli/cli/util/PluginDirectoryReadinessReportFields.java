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
 * Stable JSON field names for plugin-directory readiness sections in CI reports.
 */
public final class PluginDirectoryReadinessReportFields {
    private PluginDirectoryReadinessReportFields() {
    }

    public static final class Section {
        public static final String ACTIVE_DIRECTORIES = "activeDirectories";
        public static final String JAR_COUNT = "jarCount";
        public static final String MODEL_FAMILY_PLUGIN_CANDIDATES = "modelFamilyPluginCandidates";
        public static final String UNIFIED_RUNTIME_PLUGIN_CANDIDATES = "unifiedRuntimePluginCandidates";
        public static final String UNIFIED_RUNTIME_READY = "unifiedRuntimeReady";
        public static final String UNIFIED_RUNTIME_NOT_READY = "unifiedRuntimeNotReady";
        public static final String PLUGIN_INSTALL_READY = "pluginInstallReady";
        public static final String PLUGIN_INSTALL_NOT_READY = "pluginInstallNotReady";
        public static final String PLUGIN_TOKENIZER_METADATA_READY = "pluginTokenizerMetadataReady";
        public static final String PLUGIN_TOKENIZER_METADATA_PENDING = "pluginTokenizerMetadataPending";
        public static final String PLUGIN_TOKENIZER_METADATA_MISSING = "pluginTokenizerMetadataMissing";
        public static final String PLUGIN_TOKENIZER_METADATA_INVALID = "pluginTokenizerMetadataInvalid";
        public static final String PLUGIN_TOKENIZER_METADATA_READY_JARS = "pluginTokenizerMetadataReadyJars";
        public static final String PLUGIN_TOKENIZER_METADATA_PENDING_JARS = "pluginTokenizerMetadataPendingJars";
        public static final String PLUGIN_TOKENIZER_METADATA_MISSING_JARS = "pluginTokenizerMetadataMissingJars";
        public static final String PLUGIN_TOKENIZER_METADATA_INVALID_JARS = "pluginTokenizerMetadataInvalidJars";
        public static final String UNIFIED_RUNTIME_PLUGIN_JARS = "unifiedRuntimePluginJars";
        public static final String UNIFIED_RUNTIME_READY_JARS = "unifiedRuntimeReadyJars";
        public static final String UNIFIED_RUNTIME_NOT_READY_JARS = "unifiedRuntimeNotReadyJars";
        public static final String ERRORS = "errors";
        public static final String JARS = "jars";

        private Section() {
        }
    }

    public static final class Jar {
        public static final String PATH = "path";
        public static final String HAS_MODEL_FAMILY_SERVICE_ENTRY = "hasModelFamilyServiceEntry";
        public static final String HAS_AQLI_PLUGIN_SERVICE_ENTRY = "hasAqliPluginServiceEntry";
        public static final String AQLI_PLUGIN_PROVIDERS = "aqliPluginProviders";
        public static final String HAS_UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY =
                "hasUnifiedMultimodalRuntimeServiceEntry";
        public static final String UNIFIED_MULTIMODAL_RUNTIME_PROVIDERS = "unifiedMultimodalRuntimeProviders";
        public static final String UNIFIED_MULTIMODAL_RUNTIME_MISSING_PROVIDER_CLASSES =
                "unifiedMultimodalRuntimeMissingProviderClasses";
        public static final String UNIFIED_RUNTIME_READY = "unifiedRuntimeReady";
        public static final String UNIFIED_RUNTIME_ERRORS = "unifiedRuntimeErrors";
        public static final String HAS_PLUGIN_DESCRIPTOR = "hasPluginDescriptor";
        public static final String PLUGIN_DESCRIPTOR_ID = "pluginDescriptorId";
        public static final String PLUGIN_EXTENSION_POINT = "pluginExtensionPoint";
        public static final String PLUGIN_FAMILIES = "pluginFamilies";
        public static final String PLUGIN_BUNDLE_PROFILE = "pluginBundleProfile";
        public static final String PLUGIN_TOKENIZER_KIND = "pluginTokenizerKind";
        public static final String PLUGIN_TOKENIZER_KINDS = "pluginTokenizerKinds";
        public static final String PLUGIN_TOKENIZER_METADATA_DESCRIPTOR_STATUS =
                "pluginTokenizerMetadataDescriptorStatus";
        public static final String PLUGIN_TOKENIZER_METADATA_STATUS = "pluginTokenizerMetadataStatus";
        public static final String PLUGIN_TOKENIZER_METADATA_PENDING_REASON =
                "pluginTokenizerMetadataPendingReason";
        public static final String PLUGIN_MAIN_CLASS = "pluginMainClass";
        public static final String PLUGIN_INSTALL_CANDIDATE = "pluginInstallCandidate";
        public static final String PLUGIN_INSTALL_READY = "pluginInstallReady";
        public static final String PLUGIN_INSTALL_ERRORS = "pluginInstallErrors";
        public static final String INSPECTION_ERROR = "inspectionError";

        private Jar() {
        }
    }

    public static List<String> sectionFields() {
        return List.of(
                Section.ACTIVE_DIRECTORIES,
                Section.JAR_COUNT,
                Section.MODEL_FAMILY_PLUGIN_CANDIDATES,
                Section.UNIFIED_RUNTIME_PLUGIN_CANDIDATES,
                Section.UNIFIED_RUNTIME_READY,
                Section.UNIFIED_RUNTIME_NOT_READY,
                Section.PLUGIN_INSTALL_READY,
                Section.PLUGIN_INSTALL_NOT_READY,
                Section.PLUGIN_TOKENIZER_METADATA_READY,
                Section.PLUGIN_TOKENIZER_METADATA_PENDING,
                Section.PLUGIN_TOKENIZER_METADATA_MISSING,
                Section.PLUGIN_TOKENIZER_METADATA_INVALID,
                Section.PLUGIN_TOKENIZER_METADATA_READY_JARS,
                Section.PLUGIN_TOKENIZER_METADATA_PENDING_JARS,
                Section.PLUGIN_TOKENIZER_METADATA_MISSING_JARS,
                Section.PLUGIN_TOKENIZER_METADATA_INVALID_JARS,
                Section.UNIFIED_RUNTIME_PLUGIN_JARS,
                Section.UNIFIED_RUNTIME_READY_JARS,
                Section.UNIFIED_RUNTIME_NOT_READY_JARS,
                Section.ERRORS,
                Section.JARS);
    }

    public static List<String> jarFields() {
        return List.of(
                Jar.PATH,
                Jar.HAS_MODEL_FAMILY_SERVICE_ENTRY,
                Jar.HAS_AQLI_PLUGIN_SERVICE_ENTRY,
                Jar.AQLI_PLUGIN_PROVIDERS,
                Jar.HAS_UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY,
                Jar.UNIFIED_MULTIMODAL_RUNTIME_PROVIDERS,
                Jar.UNIFIED_MULTIMODAL_RUNTIME_MISSING_PROVIDER_CLASSES,
                Jar.UNIFIED_RUNTIME_READY,
                Jar.UNIFIED_RUNTIME_ERRORS,
                Jar.HAS_PLUGIN_DESCRIPTOR,
                Jar.PLUGIN_DESCRIPTOR_ID,
                Jar.PLUGIN_EXTENSION_POINT,
                Jar.PLUGIN_FAMILIES,
                Jar.PLUGIN_BUNDLE_PROFILE,
                Jar.PLUGIN_TOKENIZER_KIND,
                Jar.PLUGIN_TOKENIZER_KINDS,
                Jar.PLUGIN_TOKENIZER_METADATA_DESCRIPTOR_STATUS,
                Jar.PLUGIN_TOKENIZER_METADATA_STATUS,
                Jar.PLUGIN_TOKENIZER_METADATA_PENDING_REASON,
                Jar.PLUGIN_MAIN_CLASS,
                Jar.PLUGIN_INSTALL_CANDIDATE,
                Jar.PLUGIN_INSTALL_READY,
                Jar.PLUGIN_INSTALL_ERRORS,
                Jar.INSPECTION_ERROR);
    }
}
