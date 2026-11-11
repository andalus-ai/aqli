package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.Command;
import tech.kayys.aqli.cli.AqliCommand;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBenchmarksCommandTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void restoreProperties() {
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY);
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_ENABLED_PROPERTY);
        System.clearProperty(RunnerRouteBenchmarkCache.CACHE_STALE_DAYS_PROPERTY);
    }

    @Test
    void rootCommandRegistersRouteBenchmarksCommand() {
        Command annotation = AqliCommand.class.getAnnotation(Command.class);

        assertTrue(Arrays.asList(annotation.subcommands()).contains(RouteBenchmarksCommand.class));
    }

    @Test
    void listCommandPrintsJsonForAutomation() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        recordBenchmark("model-a.gguf", 11.25d, 220.0d);

        RouteBenchmarksCommand.ListCommand command = new RouteBenchmarksCommand.ListCommand();
        command.jsonOutput = true;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        JsonNode root = new ObjectMapper().readTree(output.toString(StandardCharsets.UTF_8));
        assertEquals(1, root.path("schemaVersion").asInt());
        assertTrue(root.path("enabled").asBoolean());
        assertEquals(1, root.path("entryCount").asInt());
        assertTrue(root.path("providers").isArray());
        assertTrue(root.path("formats").isArray());
        assertEquals(1, root.path("count").asInt());
        assertEquals("gguf", root.path("entries").get(0).path("provider").asText());
        assertEquals("gguf", root.path("entries").get(0).path("format").asText());
        assertTrue(root.path("entries").get(0).path("stale").isBoolean());
        assertTrue(root.path("entries").get(0).path("ageDays").canConvertToLong());
        assertEquals(11.25d, root.path("entries").get(0).path("generationTokensPerSecond").asDouble(), 0.0001d);
    }

    @Test
    void clearCommandRequiresConfirmationAndDeletesWhenConfirmed() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        recordBenchmark("model-a.gguf", 10.0d, 200.0d);
        Path cacheFile = tempDir.resolve(RunnerRouteBenchmarkCache.CACHE_FILE);
        assertTrue(Files.isRegularFile(cacheFile));

        RouteBenchmarksCommand.ClearCommand refused = new RouteBenchmarksCommand.ClearCommand();
        assertEquals(2, refused.call());
        assertTrue(Files.isRegularFile(cacheFile));

        RouteBenchmarksCommand.ClearCommand confirmed = new RouteBenchmarksCommand.ClearCommand();
        confirmed.yes = true;
        assertEquals(0, confirmed.call());
        assertFalse(Files.exists(cacheFile));
    }

    @Test
    void doctorCommandPrintsJsonHealth() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        recordBenchmark("model-a.gguf", 10.0d, 200.0d);
        RouteBenchmarksCommand.DoctorCommand command = new RouteBenchmarksCommand.DoctorCommand();
        command.jsonOutput = true;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        JsonNode root = new ObjectMapper().readTree(output.toString(StandardCharsets.UTF_8));
        assertEquals(1, root.path("schemaVersion").asInt());
        assertEquals("healthy", root.path("status").asText());
        assertEquals(1, root.path("entryCount").asInt());
        assertEquals(0, root.path("staleEntryCount").asInt());
    }

    @Test
    void doctorCommandStrictPassesForHealthyCache() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        recordBenchmark("model-a.gguf", 10.0d, 200.0d);

        RouteBenchmarksCommand.DoctorCommand command = new RouteBenchmarksCommand.DoctorCommand();
        command.strict = true;

        assertEquals(0, command.call());
    }

    @Test
    void doctorCommandStrictFailsForStaleCache() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        RunnerRouteBenchmarkCache.Entry old = recordBenchmark("old-model.gguf", 8.0d, 300.0d);
        writeEntries(staleCopy(old));

        RouteBenchmarksCommand.DoctorCommand command = new RouteBenchmarksCommand.DoctorCommand();
        command.strict = true;

        assertEquals(1, command.call());
    }

    @Test
    void pruneCommandRequiresConfirmation() {
        RouteBenchmarksCommand.PruneCommand command = new RouteBenchmarksCommand.PruneCommand();

        assertEquals(2, command.call());
    }

    @Test
    void listCommandFiltersEntries() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        recordBenchmark("gemma.gguf", 10.0d, 200.0d);
        recordBenchmark("llama.gguf", 20.0d, 120.0d);

        RouteBenchmarksCommand.ListCommand command = new RouteBenchmarksCommand.ListCommand();
        command.modelFilter = "llama";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("llama.gguf"));
        assertFalse(text.contains("gemma.gguf"));
    }

    @Test
    void listCommandPrintsFreshAndStaleState() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        RunnerRouteBenchmarkCache.Entry old = recordBenchmark("old-model.gguf", 8.0d, 300.0d);
        RunnerRouteBenchmarkCache.Entry current = recordBenchmark("current-model.gguf", 20.0d, 120.0d);
        writeEntries(staleCopy(old), current);

        RouteBenchmarksCommand.ListCommand command = new RouteBenchmarksCommand.ListCommand();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("STATE"));
        assertTrue(text.contains("AGE D"));
        assertTrue(text.contains("fresh"));
        assertTrue(text.contains("stale"));
    }

    @Test
    void listCommandFreshOnlyHidesStaleEntries() throws Exception {
        System.setProperty(RunnerRouteBenchmarkCache.CACHE_DIR_PROPERTY, tempDir.toString());
        RunnerRouteBenchmarkCache.Entry old = recordBenchmark("old-model.gguf", 8.0d, 300.0d);
        RunnerRouteBenchmarkCache.Entry current = recordBenchmark("current-model.gguf", 20.0d, 120.0d);
        writeEntries(staleCopy(old), current);

        RouteBenchmarksCommand.ListCommand command = new RouteBenchmarksCommand.ListCommand();
        command.freshOnly = true;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("current-model.gguf"));
        assertFalse(text.contains("old-model.gguf"));
    }

    private RunnerRouteBenchmarkCache.Entry recordBenchmark(String filename, double tokensPerSecond, double ttftMs)
            throws Exception {
        Path model = Files.writeString(tempDir.resolve(filename), "fake");
        RunnerRouteReport report = ggufReport();
        Map<String, Object> metrics = new LinkedHashMap<>(report.toMetadata());
        metrics.put("bench.generation_tps", tokensPerSecond);
        metrics.put("bench.ttft_ms", ttftMs);
        RunnerRouteBenchmarkCache.record("model", model.toString(), model.toString(), metrics, 10, 1000L);
        return RunnerRouteBenchmarkCache.find("model", model.toString(), model.toString(), "gguf", "gguf")
                .orElseThrow();
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

    private static RunnerRouteBenchmarkCache.Entry staleCopy(RunnerRouteBenchmarkCache.Entry entry) {
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
                System.currentTimeMillis() - 40L * 86_400_000L);
    }
}
