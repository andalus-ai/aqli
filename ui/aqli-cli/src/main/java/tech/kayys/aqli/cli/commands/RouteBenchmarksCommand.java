package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Inspect and manage cached route benchmark observations for runner plugins.
 */
@Dependent
@Unremovable
@Command(
        name = "route-benchmarks",
        aliases = { "route-benchmark", "runner-benchmarks" },
        description = "Inspect cached runner route benchmark observations",
        subcommands = {
                RouteBenchmarksCommand.ListCommand.class,
                RouteBenchmarksCommand.DoctorCommand.class,
                RouteBenchmarksCommand.PruneCommand.class,
                RouteBenchmarksCommand.ClearCommand.class
        })
public class RouteBenchmarksCommand implements Callable<Integer> {
    @Option(names = { "--json" }, description = "Print cached route benchmarks as JSON")
    boolean jsonOutput;

    @Option(names = { "--model" }, description = "Filter entries whose cached model identity contains this text")
    String modelFilter;

    @Option(names = { "--provider" }, description = "Filter entries by provider, such as gguf, litert, or safetensor")
    String providerFilter;

    @Option(names = { "--format" }, description = "Filter entries by model artifact format")
    String formatFilter;

    @Option(names = { "--fresh-only" }, description = "Only list entries that are still trusted for route profiles")
    boolean freshOnly;

    @Option(names = { "--limit" }, description = "Maximum entries to print", defaultValue = "50")
    int limit;

    @Override
    public Integer call() {
        ListCommand command = new ListCommand();
        command.jsonOutput = jsonOutput;
        command.modelFilter = modelFilter;
        command.providerFilter = providerFilter;
        command.formatFilter = formatFilter;
        command.freshOnly = freshOnly;
        command.limit = limit;
        return command.call();
    }

    /**
     * List cached benchmark observations in newest-first order.
     */
    @Command(name = "list", description = "List cached route benchmark observations")
    public static class ListCommand implements Callable<Integer> {
        @Option(names = { "--json" }, description = "Print cached route benchmarks as JSON")
        boolean jsonOutput;

        @Option(names = { "--model" }, description = "Filter entries whose cached model identity contains this text")
        String modelFilter;

        @Option(names = { "--provider" }, description = "Filter entries by provider, such as gguf, litert, or safetensor")
        String providerFilter;

        @Option(names = { "--format" }, description = "Filter entries by model artifact format")
        String formatFilter;

        @Option(names = { "--fresh-only" }, description = "Only list entries that are still trusted for route profiles")
        boolean freshOnly;

        @Option(names = { "--limit" }, description = "Maximum entries to print", defaultValue = "50")
        int limit;

        @Override
        public Integer call() {
            List<RunnerRouteBenchmarkCache.Entry> entries = filteredEntries();
            if (jsonOutput) {
                printJson(entries);
                return 0;
            }
            printTable(entries);
            return 0;
        }

        private List<RunnerRouteBenchmarkCache.Entry> filteredEntries() {
            int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
            return RunnerRouteBenchmarkCache.entries().stream()
                    .filter(entry -> matches(entry.identity(), modelFilter))
                    .filter(entry -> matches(entry.provider(), providerFilter))
                    .filter(entry -> matches(entry.format(), formatFilter))
                    .filter(entry -> !freshOnly || !RunnerRouteBenchmarkCache.isStale(entry))
                    .limit(safeLimit)
                    .toList();
        }

        private void printJson(List<RunnerRouteBenchmarkCache.Entry> entries) {
            Map<String, Object> payload = new LinkedHashMap<>(RouteBenchmarkCacheReports.summaryReport(entries, 0));
            payload.put("count", entries.size());
            payload.put("entries", entries.stream().map(RouteBenchmarkCacheReports::entryReport).toList());
            try {
                System.out.println(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(payload));
            } catch (Exception error) {
                System.err.println("Failed to serialize route benchmark cache: " + error.getMessage());
            }
        }

        private void printTable(List<RunnerRouteBenchmarkCache.Entry> entries) {
            Path cacheFile = RunnerRouteBenchmarkCache.cacheFilePath();
            if (!RunnerRouteBenchmarkCache.isEnabled()) {
                System.out.println("Route benchmark cache is disabled.");
                System.out.println("Cache file: " + cacheFile);
                return;
            }
            if (entries.isEmpty()) {
                System.out.println("No cached route benchmarks found.");
                System.out.println("Cache file: " + cacheFile);
                return;
            }
            System.out.printf("%-10s %-10s %-6s %6s %10s %10s %6s %-24s %s%n",
                    "PROVIDER",
                    "FORMAT",
                    "STATE",
                    "AGE D",
                    "TOK/S",
                    "TTFT MS",
                    "OBS",
                    "UPDATED",
                    "MODEL");
            System.out.println("-".repeat(112));
            for (RunnerRouteBenchmarkCache.Entry entry : entries) {
                System.out.printf("%-10s %-10s %-6s %6d %10.2f %10s %6d %-24s %s%n",
                        entry.provider(),
                        entry.format(),
                        RunnerRouteBenchmarkCache.isStale(entry) ? "stale" : "fresh",
                        RunnerRouteBenchmarkCache.ageDays(entry),
                        entry.generationTokensPerSecond(),
                        entry.ttftMs() == null ? "-" : String.format(Locale.ROOT, "%.0f", entry.ttftMs()),
                        entry.observations(),
                        Instant.ofEpochMilli(entry.updatedAtEpochMs()),
                        entry.identity());
            }
            System.out.println();
            System.out.println("Cache file: " + cacheFile);
        }

        private static boolean matches(String value, String filter) {
            if (filter == null || filter.isBlank()) {
                return true;
            }
            String candidate = value == null ? "" : value.toLowerCase(Locale.ROOT);
            return candidate.contains(filter.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Print benchmark-cache health and freshness diagnostics.
     */
    @Command(name = "doctor", description = "Validate route benchmark cache health and freshness")
    public static class DoctorCommand implements Callable<Integer> {
        @Option(names = { "--json" }, description = "Print cache health as JSON")
        boolean jsonOutput;

        @Option(names = { "--stale-after-days" }, description = "Age threshold for stale measurements", defaultValue = "0")
        int staleAfterDays;

        @Option(names = { "--strict" }, description = "Exit non-zero when the benchmark cache is not healthy or empty")
        boolean strict;

        @Override
        public Integer call() {
            RunnerRouteBenchmarkCache.Health health = staleAfterDays > 0
                    ? RunnerRouteBenchmarkCache.health(staleAfterDays)
                    : RunnerRouteBenchmarkCache.health();
            if (jsonOutput) {
                printJson(RouteBenchmarkCacheReports.healthReport(health));
            } else {
                printHealth(health);
            }
            return strict && !healthy(health) ? 1 : 0;
        }

        private void printHealth(RunnerRouteBenchmarkCache.Health health) {
            System.out.println("Route benchmark cache: " + health.status());
            System.out.println("  enabled: " + health.enabled());
            System.out.println("  cache file: " + health.cacheFile());
            System.out.println("  entries: " + health.entryCount());
            System.out.println("  stale entries: " + health.staleEntryCount()
                    + " (older than " + health.staleAfterDays() + " days)");
            System.out.println("  invalid lines: " + health.invalidLineCount());
            System.out.println("  readable: " + health.cacheFileReadable());
            System.out.println("  directory writable: " + health.cacheDirectoryWritable());
            if (health.newestUpdatedAtEpochMs() != null) {
                System.out.println("  newest updated: " + Instant.ofEpochMilli(health.newestUpdatedAtEpochMs()));
            }
        }

        private static boolean healthy(RunnerRouteBenchmarkCache.Health health) {
            return "healthy".equals(health.status()) || "empty".equals(health.status());
        }
    }

    /**
     * Prune stale benchmark observations after explicit confirmation.
     */
    @Command(name = "prune", description = "Prune stale route benchmark observations")
    public static class PruneCommand implements Callable<Integer> {
        @Option(names = { "--older-than-days" }, description = "Remove entries older than this many days", defaultValue = "30")
        int olderThanDays;

        @Option(names = { "--dry-run" }, description = "Show what would be pruned without modifying the cache")
        boolean dryRun;

        @Option(names = { "-y", "--yes" }, description = "Confirm pruning without an interactive prompt")
        boolean yes;

        @Option(names = { "--json" }, description = "Print prune result as JSON")
        boolean jsonOutput;

        @Override
        public Integer call() {
            if (!dryRun && !yes) {
                if (jsonOutput) {
                    printJson(Map.of(
                            "schemaVersion", 1,
                            "success", false,
                            "status", "confirmation_required",
                            "cacheFile", RunnerRouteBenchmarkCache.cacheFilePath().toString()));
                } else {
                    System.err.println("Refusing to prune route benchmark cache without --yes.");
                    System.err.println("Use --dry-run to preview stale entries without modifying the cache.");
                }
                return 2;
            }
            RunnerRouteBenchmarkCache.PruneResult result = dryRun
                    ? dryRunResult(olderThanDays)
                    : RunnerRouteBenchmarkCache.pruneOlderThanDays(olderThanDays);
            if (jsonOutput) {
                printJson(RouteBenchmarkCacheReports.pruneResultReport(result));
            } else {
                printResult(result);
            }
            return result.success() ? 0 : 1;
        }

        private RunnerRouteBenchmarkCache.PruneResult dryRunResult(int olderThanDays) {
            int normalizedDays = Math.max(1, olderThanDays);
            long cutoff = Instant.now().minusSeconds(normalizedDays * 86_400L).toEpochMilli();
            List<RunnerRouteBenchmarkCache.Entry> entries = RunnerRouteBenchmarkCache.entries();
            int staleCount = (int) entries.stream()
                    .filter(entry -> entry.updatedAtEpochMs() < cutoff)
                    .count();
            RunnerRouteBenchmarkCache.Health health = RunnerRouteBenchmarkCache.health(normalizedDays);
            return new RunnerRouteBenchmarkCache.PruneResult(
                    true,
                    staleCount > 0 || health.invalidLineCount() > 0,
                    "dry_run",
                    RunnerRouteBenchmarkCache.cacheFilePath(),
                    normalizedDays,
                    entries.size(),
                    staleCount,
                    entries.size() - staleCount,
                    health.invalidLineCount());
        }

        private void printResult(RunnerRouteBenchmarkCache.PruneResult result) {
            System.out.println("Route benchmark prune: " + result.status());
            System.out.println("  cache file: " + result.cacheFile());
            System.out.println("  older than days: " + result.olderThanDays());
            System.out.println("  before: " + result.beforeCount());
            System.out.println("  removed: " + result.removedCount());
            System.out.println("  retained: " + result.retainedCount());
            if (result.invalidLineCount() > 0) {
                System.out.println("  invalid lines removed: " + result.invalidLineCount());
            }
        }
    }

    /**
     * Clear cached benchmark observations after explicit confirmation.
     */
    @Command(name = "clear", description = "Clear cached route benchmark observations")
    public static class ClearCommand implements Callable<Integer> {
        @Option(names = { "-y", "--yes" }, description = "Confirm deletion without an interactive prompt")
        boolean yes;

        @Option(names = { "--json" }, description = "Print clear result as JSON")
        boolean jsonOutput;

        @Override
        public Integer call() {
            if (!yes) {
                if (jsonOutput) {
                    printJson(false, false, "confirmation_required");
                } else {
                    System.err.println("Refusing to clear route benchmark cache without --yes.");
                }
                return 2;
            }
            boolean deleted = RunnerRouteBenchmarkCache.clear();
            if (jsonOutput) {
                printJson(true, deleted, deleted ? "deleted" : "not_found");
            } else {
                System.out.println(deleted
                        ? "Cleared route benchmark cache: " + RunnerRouteBenchmarkCache.cacheFilePath()
                        : "No route benchmark cache file found: " + RunnerRouteBenchmarkCache.cacheFilePath());
            }
            return 0;
        }

        private void printJson(boolean confirmed, boolean deleted, String status) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", 1);
            payload.put("confirmed", confirmed);
            payload.put("deleted", deleted);
            payload.put("status", status);
            payload.put("cacheFile", RunnerRouteBenchmarkCache.cacheFilePath().toString());
            try {
                System.out.println(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(payload));
            } catch (Exception error) {
                System.err.println("Failed to serialize route benchmark clear result: " + error.getMessage());
            }
        }
    }

    private static void printJson(Map<String, Object> payload) {
        try {
            System.out.println(new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload));
        } catch (Exception error) {
            System.err.println("Failed to serialize route benchmark result: " + error.getMessage());
        }
    }
}
