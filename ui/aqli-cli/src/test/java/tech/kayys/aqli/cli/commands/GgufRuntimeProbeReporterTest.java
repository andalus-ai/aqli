package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProfile;
import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProbe;

class GgufRuntimeProbeReporterTest {

    @Test
    void reportsAllReadinessFlagsAsFalseWhenProfileIsUnavailable() {
        assertEquals(
                "loaderReady=false, decoderTensorsReady=false, rowDotReady=false, generationReady=false",
                GgufRuntimeProbeReporter.javaReadinessSummary(null));
    }

    @Test
    void reportsUnavailableRowDotProbeWithoutRunningAnyProbeWork() {
        GgufRuntimeProbe probe = mock(GgufRuntimeProbe.class);
        when(probe.hasTensorProbe()).thenReturn(false);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            GgufRuntimeProbeReporter.printRowDotProbe(out, "Java GGUF", probe);
        }

        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .contains("Java GGUF row-dot probe: unavailable, no supported matrix tensor."));
    }

    @Test
    void readinessSummaryReflectsProfileCapabilities() {
        GgufRuntimeProfile profile = mock(GgufRuntimeProfile.class);
        when(profile.knownTensorTypeRatio()).thenReturn(1.0d);
        when(profile.decoderTensorSetComplete()).thenReturn(true);
        when(profile.rowDotPrimitivesReady()).thenReturn(false);

        assertEquals(
                "loaderReady=true, decoderTensorsReady=true, rowDotReady=false, generationReady=false",
                GgufRuntimeProbeReporter.javaReadinessSummary(profile));
    }
}
