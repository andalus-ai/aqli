/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.PluginDirectoryReadinessReportFields.Jar;
import tech.kayys.aqli.cli.util.PluginDirectoryReadinessReportFields.Section;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds reusable plugin-directory readiness sections for CLI and CI reports.
 */
public final class PluginDirectoryReadinessReports {
    private PluginDirectoryReadinessReports() {
    }

    public static Map<String, Object> report(
            ExternalPluginClasspath.PluginDirectoryInspection inspection) {
        ExternalPluginClasspath.PluginDirectoryInspection effectiveInspection = inspection == null
                ? ExternalPluginClasspath.inspectPluginClasspath(List.of())
                : inspection;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Section.ACTIVE_DIRECTORIES,
                ExternalPluginClasspath.display(effectiveInspection.activeDirectories()));
        report.put(Section.JAR_COUNT, effectiveInspection.jarCount());
        report.put(Section.MODEL_FAMILY_PLUGIN_CANDIDATES, effectiveInspection.modelFamilyPluginCandidates());
        report.put(Section.UNIFIED_RUNTIME_PLUGIN_CANDIDATES, effectiveInspection.unifiedRuntimePluginCandidates());
        report.put(Section.UNIFIED_RUNTIME_READY, effectiveInspection.unifiedRuntimeReady());
        report.put(Section.UNIFIED_RUNTIME_NOT_READY, effectiveInspection.unifiedRuntimeNotReady());
        report.put(Section.PLUGIN_INSTALL_READY, effectiveInspection.pluginInstallReady());
        report.put(Section.PLUGIN_INSTALL_NOT_READY, effectiveInspection.pluginInstallNotReady());
        report.put(Section.PLUGIN_TOKENIZER_METADATA_READY, effectiveInspection.pluginTokenizerMetadataReady());
        report.put(Section.PLUGIN_TOKENIZER_METADATA_PENDING, effectiveInspection.pluginTokenizerMetadataPending());
        report.put(Section.PLUGIN_TOKENIZER_METADATA_MISSING, effectiveInspection.pluginTokenizerMetadataMissing());
        report.put(Section.PLUGIN_TOKENIZER_METADATA_INVALID, effectiveInspection.pluginTokenizerMetadataInvalid());
        report.put(Section.PLUGIN_TOKENIZER_METADATA_READY_JARS,
                ExternalPluginClasspath.display(effectiveInspection.pluginTokenizerMetadataReadyJars()));
        report.put(Section.PLUGIN_TOKENIZER_METADATA_PENDING_JARS,
                ExternalPluginClasspath.display(effectiveInspection.pluginTokenizerMetadataPendingJars()));
        report.put(Section.PLUGIN_TOKENIZER_METADATA_MISSING_JARS,
                ExternalPluginClasspath.display(effectiveInspection.pluginTokenizerMetadataMissingJars()));
        report.put(Section.PLUGIN_TOKENIZER_METADATA_INVALID_JARS,
                ExternalPluginClasspath.display(effectiveInspection.pluginTokenizerMetadataInvalidJars()));
        report.put(Section.UNIFIED_RUNTIME_PLUGIN_JARS,
                ExternalPluginClasspath.display(effectiveInspection.unifiedRuntimePluginJars()));
        report.put(Section.UNIFIED_RUNTIME_READY_JARS,
                ExternalPluginClasspath.display(effectiveInspection.unifiedRuntimeReadyJars()));
        report.put(Section.UNIFIED_RUNTIME_NOT_READY_JARS,
                ExternalPluginClasspath.display(effectiveInspection.unifiedRuntimeNotReadyJars()));
        report.put(Section.ERRORS, effectiveInspection.errors());
        report.put(Section.JARS, effectiveInspection.jars().stream()
                .map(PluginDirectoryReadinessReports::jarReport)
                .toList());
        return report;
    }

    private static Map<String, Object> jarReport(
            ExternalPluginClasspath.PluginDirectoryJarReport jar) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Jar.PATH, jar.path() == null ? "" : jar.path().toString());
        report.put(Jar.HAS_MODEL_FAMILY_SERVICE_ENTRY, jar.hasModelFamilyServiceEntry());
        report.put(Jar.HAS_AQLI_PLUGIN_SERVICE_ENTRY, jar.hasAqliPluginServiceEntry());
        report.put(Jar.AQLI_PLUGIN_PROVIDERS, jar.aqliPluginProviders());
        report.put(Jar.HAS_UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY, jar.hasUnifiedMultimodalRuntimeServiceEntry());
        report.put(Jar.UNIFIED_MULTIMODAL_RUNTIME_PROVIDERS, jar.unifiedMultimodalRuntimeProviders());
        report.put(
                Jar.UNIFIED_MULTIMODAL_RUNTIME_MISSING_PROVIDER_CLASSES,
                jar.unifiedMultimodalRuntimeMissingProviderClasses());
        report.put(Jar.UNIFIED_RUNTIME_READY, jar.unifiedRuntimeReady());
        report.put(Jar.UNIFIED_RUNTIME_ERRORS, jar.unifiedRuntimeErrors());
        report.put(Jar.HAS_PLUGIN_DESCRIPTOR, jar.hasPluginDescriptor());
        report.put(Jar.PLUGIN_DESCRIPTOR_ID, jar.pluginDescriptorId());
        report.put(Jar.PLUGIN_EXTENSION_POINT, jar.pluginExtensionPoint());
        report.put(Jar.PLUGIN_FAMILIES, jar.pluginFamilies());
        report.put(Jar.PLUGIN_BUNDLE_PROFILE, jar.pluginBundleProfile());
        report.put(Jar.PLUGIN_TOKENIZER_KIND, jar.pluginTokenizerKind());
        report.put(Jar.PLUGIN_TOKENIZER_KINDS, jar.pluginTokenizerKinds());
        report.put(Jar.PLUGIN_TOKENIZER_METADATA_DESCRIPTOR_STATUS, jar.pluginTokenizerMetadataDescriptorStatus());
        report.put(Jar.PLUGIN_TOKENIZER_METADATA_STATUS, jar.pluginTokenizerMetadataStatus());
        report.put(Jar.PLUGIN_TOKENIZER_METADATA_PENDING_REASON, jar.pluginTokenizerMetadataPendingReason());
        report.put(Jar.PLUGIN_MAIN_CLASS, jar.pluginMainClass());
        report.put(Jar.PLUGIN_INSTALL_CANDIDATE, jar.pluginInstallCandidate());
        report.put(Jar.PLUGIN_INSTALL_READY, jar.pluginInstallReady());
        report.put(Jar.PLUGIN_INSTALL_ERRORS, jar.pluginInstallErrors());
        report.put(Jar.INSPECTION_ERROR, jar.inspectionError());
        return report;
    }
}
