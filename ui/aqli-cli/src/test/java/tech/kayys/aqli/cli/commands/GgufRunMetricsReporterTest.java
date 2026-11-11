package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GgufRunMetricsReporterTest {

    @Test
    void parsesNativeMetricsWithoutLosingValuesContainingEquals() {
        Map<String, String> metrics = GgufRunMetricsReporter.parseNativeMetrics(
                "backend=metal,modelLoad=12.5ms,tokenizeCache=hit,extra=a=b,ignored");

        assertEquals("metal", metrics.get("backend"));
        assertEquals("12.5ms", metrics.get("modelLoad"));
        assertEquals("hit", metrics.get("tokenizeCache"));
        assertEquals("a=b", metrics.get("extra"));
        assertEquals(4, metrics.size());
    }

    @Test
    void printsRunAndNativeMetrics() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            GgufRunMetricsReporter.printFastRunStats(
                    out,
                    "test GGUF",
                    20,
                    0L,
                    100_000_000L,
                    2_000_000_000L,
                    2_100_000_000L,
                    Optional.of("backend=metal,prefill=100,promptTokens=10,decode=200,decodedTokens=20"));
        }

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[test GGUF, Duration: 2.10s"));
        assertTrue(output.contains("native backend = metal"));
        assertTrue(output.contains("prompt prefill"));
        assertTrue(output.contains("generation"));
    }
}
