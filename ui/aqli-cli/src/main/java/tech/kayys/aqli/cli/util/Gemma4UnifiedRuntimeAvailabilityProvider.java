/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.plugin.core.ExtensionAvailability;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityProvider;
import tech.kayys.aqli.spi.multimodal.UnifiedInputModality;
import tech.kayys.aqli.spi.multimodal.UnifiedMultimodalRuntime;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifest;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifestViolation;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeRegistry;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeReadiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Availability signal for the detachable Gemma 4 unified multimodal runtime.
 */
public final class Gemma4UnifiedRuntimeAvailabilityProvider implements ExtensionAvailabilityProvider {
    public static final String ID = "gemma4-unified-runtime";

    private static final String FAMILY_ID = "gemma4";
    private static final String MODEL_TYPE = "gemma4_unified";
    private static final String CHECKPOINT_ID = "google/gemma-4-12B-it";
    private static final List<String> BASE_CAPABILITIES = List.of(
            "unified_multimodal_embedding",
            "gemma4_unified",
            "image_text_to_text",
            "audio_text_to_text",
            "video_text_to_text");
    private static final List<String> BASE_FORMATS = List.of(
            "hf_config",
            "processor_config",
            "tokenizer",
            "safetensors");

    @Override
    public String extensionId() {
        return ID;
    }

    @Override
    public String extensionName() {
        return "Gemma 4 Unified Runtime";
    }

    @Override
    public String extensionKind() {
        return "multimodal-runtime";
    }

    @Override
    public ExtensionAvailability availability() {
        List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> reports = discoverRuntimes();
        if (reports.isEmpty()) {
            return detachedAvailability();
        }
        if (reports.size() > 1) {
            return conflictAvailability(reports);
        }
        return attachedAvailability(reports.getFirst());
    }

    private ExtensionAvailability detachedAvailability() {
        Map<String, String> attributes = new LinkedHashMap<>(baseAttributes());
        attributes.put("runtimeAttached", "false");
        attributes.put("readiness", UnifiedRuntimeReadiness.UNAVAILABLE.statusLabel());
        attributes.put("extensionBoundary", "model-family-plugin-ready-runtime-detached");
        return new ExtensionAvailability(
                extensionId(),
                extensionName(),
                extensionKind(),
                false,
                true,
                true,
                false,
                "detached",
                BASE_CAPABILITIES,
                BASE_FORMATS,
                attributes,
                "No ServiceLoader provider for "
                        + UnifiedMultimodalRuntime.class.getName()
                        + " claims "
                        + MODEL_TYPE
                        + ".",
                List.of(
                        "Attach a runtime plugin that implements "
                                + UnifiedMultimodalRuntime.class.getName()
                                + " and advertises model type "
                                + MODEL_TYPE
                                + ".",
                        "Keep "
                                + CHECKPOINT_ID
                                + " out of direct production safetensor bundles until the unified embedder runtime is attached."));
    }

    private ExtensionAvailability attachedAvailability(UnifiedRuntimeRegistry.UnifiedRuntimeReport report) {
        UnifiedRuntimeManifest manifest = report.manifest();
        List<UnifiedRuntimeManifestViolation> violations = report.violations();
        if (!violations.isEmpty()) {
            return invalidManifestAvailability(manifest, violations);
        }
        UnifiedRuntimeReadiness readiness = manifest.readiness();
        List<String> capabilities = new ArrayList<>(BASE_CAPABILITIES);
        capabilities.add("runtime:" + manifest.runtimeId());
        Map<String, String> attributes = new LinkedHashMap<>(baseAttributes());
        attributes.put("runtimeAttached", Boolean.toString(manifest.attached()));
        attributes.put("runtimeId", manifest.runtimeId());
        attributes.put("readiness", readiness.statusLabel());
        attributes.put("readinessReason", manifest.readinessReason());
        attributes.put("inputModalities", joinModalities(manifest.inputModalities()));
        attributes.put("requiredProcessorFiles", String.join(",", manifest.requiredProcessorFiles()));
        attributes.put("requiredTokenizerFiles", String.join(",", manifest.requiredTokenizerFiles()));
        manifest.metadata().forEach((key, value) -> attributes.put("manifest." + key, value));
        return new ExtensionAvailability(
                extensionId(),
                extensionName(),
                extensionKind(),
                manifest.attached(),
                !manifest.attached(),
                readiness != UnifiedRuntimeReadiness.UNAVAILABLE,
                manifest.productionReady(),
                readiness.statusLabel(),
                capabilities,
                BASE_FORMATS,
                attributes,
                manifest.readinessReason(),
                remediationHints(readiness));
    }

    private ExtensionAvailability invalidManifestAvailability(
            UnifiedRuntimeManifest manifest,
            List<UnifiedRuntimeManifestViolation> violations) {
        Map<String, String> attributes = new LinkedHashMap<>(baseAttributes());
        attributes.put("runtimeAttached", "true");
        attributes.put("runtimeId", manifest.runtimeId());
        attributes.put("readiness", manifest.readiness().statusLabel());
        attributes.put("manifestViolationCount", Integer.toString(violations.size()));
        attributes.put("manifestViolations", violationSummaries(violations));
        return new ExtensionAvailability(
                extensionId(),
                extensionName(),
                extensionKind(),
                true,
                false,
                false,
                false,
                "invalid",
                BASE_CAPABILITIES,
                BASE_FORMATS,
                attributes,
                "Unified runtime manifest contract failed: " + violationSummaries(violations),
                List.of(
                        "Fix the UnifiedRuntimeManifest advertised by runtime "
                                + manifest.runtimeId()
                                + " before enabling Gemma 4 unified inference."));
    }

    private ExtensionAvailability conflictAvailability(List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> reports) {
        Map<String, String> attributes = new LinkedHashMap<>(baseAttributes());
        attributes.put("runtimeAttached", "true");
        attributes.put("runtimeConflict", "true");
        attributes.put("runtimeConflictCount", Integer.toString(reports.size()));
        attributes.put("runtimeIds", runtimeIds(reports));
        return new ExtensionAvailability(
                extensionId(),
                extensionName(),
                extensionKind(),
                true,
                false,
                false,
                false,
                "conflict",
                BASE_CAPABILITIES,
                BASE_FORMATS,
                attributes,
                "Multiple unified runtimes claim "
                        + MODEL_TYPE
                        + ": "
                        + runtimeIds(reports),
                List.of(
                        "Detach duplicate Gemma 4 unified runtime plugins or keep only one provider claiming "
                                + MODEL_TYPE
                                + " on the runtime classpath."));
    }

    private List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> discoverRuntimes() {
        Set<ClassLoader> classLoaders = new LinkedHashSet<>();
        addClassLoader(classLoaders, Thread.currentThread().getContextClassLoader());
        addClassLoader(classLoaders, Gemma4UnifiedRuntimeAvailabilityProvider.class.getClassLoader());
        addClassLoader(classLoaders, UnifiedMultimodalRuntime.class.getClassLoader());
        for (ClassLoader classLoader : classLoaders) {
            List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> reports =
                    UnifiedRuntimeRegistry.discover(classLoader).reportsSupportingModelType(MODEL_TYPE);
            if (!reports.isEmpty()) {
                return reports;
            }
        }
        return List.of();
    }

    private static Map<String, String> baseAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("modelFamilyId", FAMILY_ID);
        attributes.put("modelType", MODEL_TYPE);
        attributes.put("checkpoint", CHECKPOINT_ID);
        attributes.put("runtimeSpi", UnifiedMultimodalRuntime.class.getName());
        return attributes;
    }

    private static List<String> remediationHints(UnifiedRuntimeReadiness readiness) {
        return switch (readiness) {
            case READY -> List.of();
            case EXPERIMENTAL -> List.of(
                    "Use Gemma 4 unified runtime only in explicitly opted-in environments until it graduates to READY.");
            case PENDING -> List.of(
                    "Keep Gemma 4 unified out of default production inference bundles until the runtime reports READY.");
            case UNAVAILABLE -> List.of(
                    "Attach a Gemma 4 unified runtime plugin before enabling production inference.");
        };
    }

    private static String joinModalities(List<UnifiedInputModality> modalities) {
        return modalities.stream()
                .map(Enum::name)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    private static String violationSummaries(List<UnifiedRuntimeManifestViolation> violations) {
        return violations.stream()
                .map(UnifiedRuntimeManifestViolation::summary)
                .collect(Collectors.joining("; "));
    }

    private static String runtimeIds(List<UnifiedRuntimeRegistry.UnifiedRuntimeReport> reports) {
        return reports.stream()
                .map(UnifiedRuntimeRegistry.UnifiedRuntimeReport::runtimeId)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static void addClassLoader(Set<ClassLoader> classLoaders, ClassLoader classLoader) {
        if (classLoader != null) {
            classLoaders.add(classLoader);
        }
    }
}
