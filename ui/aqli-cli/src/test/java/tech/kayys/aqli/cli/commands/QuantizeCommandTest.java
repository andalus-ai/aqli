package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class QuantizeCommandTest {

    @Test
    void quantizationFailsClosedUntilARealEngineIsConnected() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new QuantizeCommand())
                .setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--model", "./model", "--strategy", "awq", "--bits", "4");

        assertEquals(2, exitCode);
        String message = stderr.toString(StandardCharsets.UTF_8);
        assertTrue(message.contains("Quantization is currently unavailable"));
        assertTrue(message.contains("No model files were copied, modified, or registered"));
    }
}
