package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.aqli.sdk.route.RunnerRouteReportFields;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerRouteBenchmarkCacheTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void restoreProperties() {
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY);
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_ENABLED_PROPERTY);
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_STALE_DAYS_PROPERTY);
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_ALLOW_STALE_PROPERTY);
    }

    @Test
    void recordsAndLoadsBenchmarkProfileForRouteReport() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        Path model = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> metrics = new LinkedHashMap<>(report.toMetadata());
        metrics.put("bench.generation_tps", 12.5d);
        metrics.put("bench.ttft_ms", 150.0d);

        RunnerRouteBenchmarkCache.record(
                "local-model",
                model.toString(),
                model.toString(),
                metrics,
                25,
                2000L);

        RunnerRoutePerformanceProfile profile = RunnerRouteBenchmarkCache.profileFor(
                        report,
                        "local-model",
                        model.toString(),
                        model.toString())
                .orElseThrow();

        assertEquals(RunnerRouteReportFields.RouteProfileStatus.SELECTED, profile.status());
        assertEquals(RunnerRouteReportFields.RouteProfileSource.BENCHMARK_CACHE, profile.source());
        assertEquals("gguf", profile.provider());
        assertEquals("gguf", profile.format());
        assertTrue(profile.reason().contains("12.50 tok/s"));
        assertTrue(profile.reason().contains("TTFT 150 ms"));
        assertTrue(profile.advice().contains("future auto routing"));
    }

    @Test
    void repeatedRecordsAreAveraged() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        Path model = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> first = new LinkedHashMap<>(report.toMetadata());
        first.put("bench.generation_tps", 10.0d);
        first.put("bench.ttft_ms", 100.0d);
        Map<String, Object> second = new LinkedHashMap<>(report.toMetadata());
        second.put("bench.generation_tps", 20.0d);
        second.put("bench.ttft_ms", 200.0d);

        RunnerRouteBenchmarkCache.record("model", model.toString(), model.toString(), first, 10, 1000L);
        RunnerRouteBenchmarkCache.record("model", model.toString(), model.toString(), second, 20, 1000L);

        RunnerRouteBenchmarkCache.Entry entry = RunnerRouteBenchmarkCache.find(
                        "model",
                        model.toString(),
                        model.toString(),
                        "gguf",
                        "gguf")
                .orElseThrow();

        assertEquals(2, entry.observations());
        assertEquals(15.0d, entry.generationTokensPerSecond(), 0.0001d);
        assertEquals(150.0d, entry.ttftMs(), 0.0001d);
    }

    @Test
    void disabledCacheDoesNotWrite() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_ENABLED_PROPERTY, "false");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> metrics = new LinkedHashMap<>(report.toMetadata());
        metrics.put("bench.generation_tps", 12.5d);

        RunnerRouteBenchmarkCache.record("model", "model", "model", metrics, 10, 1000L);

        assertFalse(Files.exists(tempDir.resolve(RunnerRouteBenchmarkCache.CACHE_FILE)));
        assertTrue(RunnerRouteBenchmarkCache.find("model", "model", "model", "gguf", "gguf").isEmpty());
    }

    @Test
    void missingRouteMetadataDoesNotWrite() {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        Map<String, Object> metrics = Map.of("bench.generation_tps", 12.5d);

        RunnerRouteBenchmarkCache.record("model", "model", "model", metrics, 10, 1000L);

        assertFalse(Files.exists(tempDir.resolve(RunnerRouteBenchmarkCache.CACHE_FILE)));
    }

    @Test
    void healthReportsStaleEntries() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        long now = System.currentTimeMillis();
        writeEntries(
                entry("old", now - 40L * 86_400_000L),
                entry("fresh", now));

        RunnerRouteBenchmarkCache.Health health = RunnerRouteBenchmarkCache.health(30);

        assertEquals("stale", health.status());
        assertEquals(2, health.entryCount());
        assertEquals(1, health.staleEntryCount());
        assertEquals(0, health.invalidLineCount());
    }

    @Test
    void staleBenchmarkProfileIsIgnoredByDefault() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        Path model = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> metrics = new LinkedHashMap<>(report.toMetadata());
        metrics.put("bench.generation_tps", 12.5d);
        RunnerRouteBenchmarkCache.record("model", model.toString(), model.toString(), metrics, 10, 1000L);
        RunnerRouteBenchmarkCache.Entry fresh = RunnerRouteBenchmarkCache.entries().get(0);
        writeEntries(staleCopy(fresh, System.currentTimeMillis() - 40L * 86_400_000L));

        assertTrue(RunnerRouteBenchmarkCache.find("model", model.toString(), model.toString(), "gguf", "gguf")
                .isPresent());
        assertTrue(RunnerRouteBenchmarkCache.profileFor(report, "model", model.toString(), model.toString())
                .isEmpty());
    }

    @Test
    void staleBenchmarkProfileCanBeAllowedForDiagnostics() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_ALLOW_STALE_PROPERTY, "true");
        Path model = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> metrics = new LinkedHashMap<>(report.toMetadata());
        metrics.put("bench.generation_tps", 12.5d);
        RunnerRouteBenchmarkCache.record("model", model.toString(), model.toString(), metrics, 10, 1000L);
        RunnerRouteBenchmarkCache.Entry fresh = RunnerRouteBenchmarkCache.entries().get(0);
        writeEntries(staleCopy(fresh, System.currentTimeMillis() - 40L * 86_400_000L));

        RunnerRoutePerformanceProfile profile = RunnerRouteBenchmarkCache.profileFor(
                        report,
                        "model",
                        model.toString(),
                        model.toString())
                .orElseThrow();

        assertEquals(RunnerRouteReportFields.RouteProfileSource.BENCHMARK_CACHE, profile.source());
    }

    @Test
    void pruneRemovesStaleAndInvalidEntries() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        long now = System.currentTimeMillis();
        Files.writeString(
                tempDir.resolve(RunnerRouteBenchmarkCache.CACHE_FILE),
                entry("old", now - 40L * 86_400_000L).serialize()
                        + System.lineSeparator()
                        + "not-a-valid-entry"
                        + System.lineSeparator()
                        + entry("fresh", now).serialize()
                        + System.lineSeparator());

        RunnerRouteBenchmarkCache.PruneResult result = RunnerRouteBenchmarkCache.pruneOlderThanDays(30);

        assertTrue(result.success());
        assertTrue(result.changed());
        assertEquals("pruned", result.status());
        assertEquals(2, result.beforeCount());
        assertEquals(1, result.removedCount());
        assertEquals(1, result.retainedCount());
        assertEquals(1, result.invalidLineCount());
        assertEquals(1, RunnerRouteBenchmarkCache.entries().size());
        assertEquals("fresh", RunnerRouteBenchmarkCache.entries().get(0).identity());
    }

    private static RunnerRouteReport ggufReport() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        return RunnerRouteReport.from(null, null, false, null, selection)
                .withEffectiveRoute("gguf", "gguf");
    }

    private void writeEntries(RunnerRouteBenchmarkCache.Entry... entries) throws Exception {
        StringBuilder content = new StringBuilder();
        for (RunnerRouteBenchmarkCache.Entry entry : entries) {
            content.append(entry.serialize()).append(System.lineSeparator());
        }
        Files.writeString(tempDir.resolve(RunnerRouteBenchmarkCache.CACHE_FILE), content.toString());
    }

    private static RunnerRouteBenchmarkCache.Entry entry(String identity, long updatedAtEpochMs) {
        return new RunnerRouteBenchmarkCache.Entry(
                "sha256:" + identity,
                identity,
                "gguf",
                "gguf",
                10.0d,
                100.0d,
                10,
                1000L,
                1,
                updatedAtEpochMs);
    }

    private static RunnerRouteBenchmarkCache.Entry staleCopy(
            RunnerRouteBenchmarkCache.Entry entry,
            long updatedAtEpochMs) {
        return new RunnerRouteBenchmarkCache.Entry(
                entry.key(),
                entry.identity(),
                entry.provider(),
                entry.format(),
                entry.generationTokensPerSecond(),
                entry.ttftMs(),
                entry.outputTokens(),
                entry.durationMs(),
                entry.observations(),
                updatedAtEpochMs);
    }
}
