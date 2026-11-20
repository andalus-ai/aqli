package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunResponseTelemetryRecorderTest {
    @Test
    void runnerOverridesEngineForTelemetryDimension() {
        assertEquals("direct", RunResponseTelemetryRecorder.resolveEngine(Map.of(
                "engine", "java",
                "runner", "direct")));
    }

    @Test
    void engineFallsBackToStandardWhenMetadataDoesNotSpecifyIt() {
        assertEquals("standard", RunResponseTelemetryRecorder.resolveEngine(Map.of()));
        assertEquals("metal", RunResponseTelemetryRecorder.resolveEngine(Map.of("engine", "metal")));
    }

    @Test
    void computesTokenLatencyAndHandlesEmptyOutput() {
        assertEquals(12.5, RunResponseTelemetryRecorder.tokenLatencyMillis(100, 8));
        assertEquals(0.0, RunResponseTelemetryRecorder.tokenLatencyMillis(100, 0));
        assertEquals(0.0, RunResponseTelemetryRecorder.tokenLatencyMillis(100, -1));
    }
}
