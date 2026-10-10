/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders human-readable model-family bundle diagnostics for CLI error messages.
 */
public final class ModelFamilyBundleDiagnosticsRenderer {
    private ModelFamilyBundleDiagnosticsRenderer() {
    }

    public static String render(ModelFamilyBundleManifest manifest, Set<String> discoveredFamilyIds) {
        StringBuilder sb = new StringBuilder();
        append(sb, manifest, discoveredFamilyIds);
        return sb.toString();
    }

    public static void append(
            StringBuilder sb,
            ModelFamilyBundleManifest manifest,
            Set<String> discoveredFamilyIds) {
        if (sb == null || manifest == null || !manifest.present()) {
            return;
        }
        Set<String> discoveredFamilies = discoveredFamilyIds == null
                ? Set.of()
                : new LinkedHashSet<>(discoveredFamilyIds);
        PluginAvailabilityChecker.ModelFamilyBundleAvailability availability =
                PluginAvailabilityChecker.modelFamilyBundleAvailability(manifest, discoveredFamilies);

        sb.append("Packaged model-family bundle: selectors=")
                .append(manifest.joinedSelectors())
                .append("; selectorSource=")
                .append(manifest.displaySelectorSource())
                .append("; preset=")
                .append(manifest.displayBundlePreset())
                .append("; policySource=")
                .append(manifest.displayPolicySource())
                .append("; fingerprint=")
                .append(manifest.displayFingerprint())
                .append("; profiles=")
                .append(manifest.displayProfiles())
                .append("; families=")
                .append(manifest.displayFamilies())
                .append("\n");

        sb.append("Packaged model-family bundle policy: ")
                .append(manifest.displayBundlePolicyStatus())
                .append("\n");
        sb.append("Packaged model-family production safety: ")
                .append(manifest.displayProductionSafetyStatus())
                .append("\n");
        sb.append("Packaged model-family catalog readiness: ")
                .append(manifest.displayCatalogReadinessStatus())
                .append("\n");
        appendFixtureDiagnostics(sb, manifest.fixtureStatus());
        sb.append("Packaged model-family bundle availability: ")
                .append(availability.compactSummary())
                .append("\n");
        appendAvailabilityDetails(sb, availability);
        appendBundlePolicyViolations(sb, "Packaged bundle policy", manifest.bundlePolicy().violations());

        if (manifest.hasBundlePreset()) {
            sb.append("Active model-family bundle preset: ")
                    .append(activeBundlePresetSummary(manifest))
                    .append("\n");
            sb.append("Active model-family bundle preset policy: ")
                    .append(manifest.displayBundlePresetPolicyStatus())
                    .append("\n");
            sb.append("Active model-family bundle preset production safety: ")
                    .append(manifest.activeBundlePreset()
                            .map(ModelFamilyBundleManifest.BundlePreset::productionSafetyCompactStatus)
                            .orElse("unknown (preset metadata missing)"))
                    .append("\n");
            sb.append("Active model-family bundle preset conformance: ")
                    .append(manifest.displayActiveBundlePresetConformance())
                    .append("\n");
            manifest.activeBundlePreset().ifPresent(preset ->
                    appendActiveModelFamilyBundlePresetViolations(sb, preset));
        }

        if (!manifest.bundlePresets().isEmpty()) {
            sb.append("Available model-family bundle presets: ")
                    .append(compactPresetSummaries(manifest))
                    .append("\n");
        }

        if (!manifest.requestedAliases().isEmpty()) {
            sb.append("Requested model-family selector aliases: ")
                    .append(manifest.joinedRequestedAliases())
                    .append("\n");
        }

        if (!manifest.unknownSelectors().isEmpty()) {
            sb.append("Unknown packaged selector metadata: ")
                    .append(String.join(", ", manifest.unknownSelectors()))
                    .append("\n");
        }

        List<String> completeAliases = completeAliasSummaries(manifest);
        if (!completeAliases.isEmpty()) {
            sb.append("Complete model-family selector aliases: ")
                    .append(String.join(", ", completeAliases))
                    .append("\n");
        }

        List<String> partialAliases = partialAliasSummaries(manifest);
        if (!partialAliases.isEmpty()) {
            sb.append("Partial model-family selector aliases: ")
                    .append(String.join(", ", partialAliases))
                    .append("\n");
        }

        if (manifest.detached()) {
            sb.append("Model-family plugins are intentionally detached in this CLI build.\n");
            sb.append("Attach external model-family plugins with --plugin-dir, --plugin-classpath, "
                    + "or AQLI_PLUGIN_DIRS when needed.\n");
        } else if (!manifest.omittedFamilies().isEmpty()) {
            sb.append("Unbundled model-family plugins: ")
                    .append(manifest.joinedOmittedFamiliesWithProfiles())
                    .append("\n");
        }

        List<String> missing = manifest.missingDiscovered(discoveredFamilies);
        if (!missing.isEmpty()) {
            sb.append("Bundled model-family plugins not discovered: ")
                    .append(String.join(", ", missing))
                    .append("\n");
        }
    }

    private static String activeBundlePresetSummary(ModelFamilyBundleManifest manifest) {
        return manifest.activeBundlePreset()
                .map(ModelFamilyBundleManifest.BundlePreset::compactSummary)
                .orElse(manifest.bundlePreset() + "(metadata=missing)");
    }

    private static String compactPresetSummaries(ModelFamilyBundleManifest manifest) {
        return manifest.bundlePresets().stream()
                .map(ModelFamilyBundleManifest.BundlePreset::compactSummary)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static List<String> completeAliasSummaries(ModelFamilyBundleManifest manifest) {
        return manifest.completeBundleAliases().stream()
                .map(ModelFamilyBundleManifest.BundleAliasCoverage::compactSummary)
                .toList();
    }

    private static List<String> partialAliasSummaries(ModelFamilyBundleManifest manifest) {
        return manifest.partialBundleAliases().stream()
                .map(ModelFamilyBundleManifest.BundleAliasCoverage::compactSummary)
                .toList();
    }

    private static void appendFixtureDiagnostics(
            StringBuilder sb,
            ModelFamilyBundleManifest.FixtureStatus fixtureStatus) {
        sb.append("Packaged model-family fixture status: ")
                .append(fixtureStatus.compactStatus())
                .append("\n");
        if (!fixtureStatus.missingRequiredFamilies().isEmpty()) {
            sb.append("Packaged model-family missing required fixtures: ")
                    .append(String.join(", ", fixtureStatus.missingRequiredFamilies()))
                    .append("\n");
        }
        if (!fixtureStatus.problemFamilies().isEmpty()) {
            sb.append("Packaged model-family problem fixtures: ")
                    .append(String.join(", ", fixtureStatus.problemFamilies()))
                    .append("\n");
        }
    }

    private static void appendAvailabilityDetails(
            StringBuilder sb,
            PluginAvailabilityChecker.ModelFamilyBundleAvailability availability) {
        if (!availability.problems().isEmpty()) {
            sb.append("Packaged model-family bundle problems: ")
                    .append(String.join("; ", availability.problems()))
                    .append("\n");
        }
        if (!availability.remediationHints().isEmpty()) {
            sb.append("Packaged model-family bundle hints: ")
                    .append(String.join("; ", availability.remediationHints()))
                    .append("\n");
        }
    }

    private static void appendActiveModelFamilyBundlePresetViolations(
            StringBuilder sb,
            ModelFamilyBundleManifest.BundlePreset preset) {
        boolean productionSafetyPassed = !preset.productionSafetyStatusKnown()
                || Boolean.TRUE.equals(preset.productionSafetyPassed());
        if ((Boolean.TRUE.equals(preset.policyPassed()) || preset.policyViolationCount() == 0)
                && productionSafetyPassed) {
            return;
        }

        appendBundlePolicyViolations(sb, "Active preset", preset.policyViolations());
        if (!productionSafetyPassed && !preset.pendingTokenizerFamilies().isEmpty()) {
            sb.append("Active preset pending tokenizer families: ")
                    .append(preset.pendingTokenizerFamilies().stream()
                            .map(familyId -> pendingTokenizerFamilySummary(preset, familyId))
                            .reduce((left, right) -> left + ", " + right)
                            .orElse(""))
                    .append("\n");
        }
    }

    private static String pendingTokenizerFamilySummary(
            ModelFamilyBundleManifest.BundlePreset preset,
            String familyId) {
        String reason = preset.pendingTokenizerReasons().get(familyId);
        return reason == null || reason.isBlank() ? familyId : familyId + " (" + reason + ")";
    }

    private static void appendBundlePolicyViolations(
            StringBuilder sb,
            String label,
            ModelFamilyBundleManifest.BundlePolicyViolations violations) {
        if (!violations.missingRequiredFamilies().isEmpty()) {
            sb.append(label).append(" missing required families: ")
                    .append(String.join(", ", violations.missingRequiredFamilies()))
                    .append("\n");
        }
        if (!violations.selectedForbiddenFamilies().isEmpty()) {
            sb.append(label).append(" selected forbidden families: ")
                    .append(String.join(", ", violations.selectedForbiddenFamilies()))
                    .append("\n");
        }
        appendAliasFamilyViolations(
                sb,
                label + " missing required alias ",
                violations.missingRequiredAliases());
        appendAliasFamilyViolations(
                sb,
                label + " selected forbidden alias ",
                violations.selectedForbiddenAliases());
    }

    private static void appendAliasFamilyViolations(
            StringBuilder sb,
            String prefix,
            Map<String, List<String>> violations) {
        violations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(prefix)
                        .append(entry.getKey())
                        .append(": ")
                        .append(String.join(", ", entry.getValue()))
                        .append("\n"));
    }
}
