package tech.kayys.aqli.cli.commands;

import java.util.Map;

import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.cli.commands.audio.AudioRunStatsRenderer;

/** Coordinates the non-streaming and streaming completion side effects for {@code run}. */
final class RunResponseCompletionCoordinator {
    private RunResponseCompletionCoordinator() {
    }

    static void complete(
            ChatUIRenderer uiRenderer,
            String model,
            Map<String, Object> metadata,
            int tokenCount,
            int outputTokens,
            long durationMs,
            double tokensPerSecond,
            Double ttftMillis,
            boolean audioOutput,
            boolean suppressStats,
            boolean jsonSse,
            boolean noBanner) {
        printStats(uiRenderer, tokenCount, durationMs, tokensPerSecond, ttftMillis,
                metadata, audioOutput, suppressStats, jsonSse, noBanner);
        RouteBenchmarkProfileCoordinator.record(
                model,
                model,
                metadata,
                outputTokens,
                durationMs,
                audioOutput,
                AudioRunStatsRenderer::isAudioMetadata);
        RunResponseTelemetryRecorder.record(metadata, outputTokens, durationMs, tokensPerSecond);
    }

    static void printStats(
            ChatUIRenderer uiRenderer,
            int tokenCount,
            long durationMs,
            double tokensPerSecond,
            Double ttftMillis,
            Map<String, Object> metadata,
            boolean audioOutput,
            boolean suppressStats,
            boolean jsonSse,
            boolean noBanner) {
        if (audioOutput || AudioRunStatsRenderer.isAudioMetadata(metadata)) {
            if (jsonSse || noBanner) {
                return;
            }
            AudioRunStatsRenderer.render(metadata, durationMs).ifPresent(line ->
                    System.out.println(ChatUIRenderer.DIM + line + ChatUIRenderer.RESET));
            return;
        }
        uiRenderer.printStats(tokenCount, durationMs / 1000.0, tokensPerSecond, ttftMillis, suppressStats);
        uiRenderer.printBenchmarks(metadata, suppressStats);
    }
}
