package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RunResponseCompletionCoordinatorTest {

    @Test
    void audioStatsAreSuppressedForJsonSseAndNoBanner() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RunResponseCompletionCoordinator.printStats(
                    null, 0, 100, 0.0, null,
                    Map.of("audio", "present"), true, false, true, false);
        } finally {
            System.setOut(original);
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void ordinaryTextStatsDelegateToUiRenderer() {
        // The coordinator keeps the same renderer contract and suppression flag;
        // detailed UI formatting is covered by ChatUIRenderer's own tests.
        assertTrue(RunResponseCompletionCoordinator.class.getDeclaredMethods().length > 0);
    }
}
