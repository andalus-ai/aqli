package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RunResponseCompletionCoordinatorTest {

    @Test
    void audioStatsAreSuppressedForJsonSse() {
        assertAudioStatsSuppressed(true, false);
    }

    @Test
    void audioStatsAreSuppressedWhenBannerIsDisabled() {
        assertAudioStatsSuppressed(false, true);
    }

    private static void assertAudioStatsSuppressed(boolean jsonSse, boolean noBanner) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RunResponseCompletionCoordinator.printStats(
                    null, 0, 100, 0.0, null,
                    Map.of("audio", "present"), true, false, jsonSse, noBanner);
        } finally {
            System.setOut(original);
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }
}
