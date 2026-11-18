package tech.kayys.aqli.cli.commands;

import java.util.Map;
import java.util.function.Predicate;

import tech.kayys.aqli.sdk.route.RunnerRouteReport;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;

/**
 * Coordinates route benchmark profile lookup and recording for the CLI.
 *
 * <p>It delegates persistence and profile matching to the SDK route cache. The CLI supplies
 * its audio-metadata predicate so audio responses retain their existing exclusion behavior.</p>
 */
final class RouteBenchmarkProfileCoordinator {

    private RouteBenchmarkProfileCoordinator() {}

    static RunnerRouteReport applyCachedProfile(
            RunnerRouteReport report,
            String requestedModel,
            String effectiveModel,
            String localPath) {
        return RunnerRouteBenchmarkCache.profileFor(report, requestedModel, effectiveModel, localPath)
                .map(report::withRouteProfile)
                .orElse(report);
    }

    static void record(
            String model,
            String localPath,
            Map<String, Object> metadata,
            int outputTokens,
            long durationMs,
            boolean audioOutput,
            Predicate<Map<String, Object>> isAudioMetadata) {
        if (!shouldRecord(metadata, audioOutput, isAudioMetadata)) {
            return;
        }
        RunnerRouteBenchmarkCache.record(
                model,
                model,
                localPath,
                metadata,
                outputTokens,
                durationMs);
    }

    static boolean shouldRecord(
            Map<String, Object> metadata,
            boolean audioOutput,
            Predicate<Map<String, Object>> isAudioMetadata) {
        return !audioOutput && !isAudioMetadata.test(metadata);
    }
}
