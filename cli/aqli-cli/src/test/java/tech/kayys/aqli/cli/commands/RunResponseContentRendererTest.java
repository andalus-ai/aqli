package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceResponse;

class RunResponseContentRendererTest {

    @Test
    void rendersJsonResponseAndSignalsThatTextRenderingShouldStop() {
        InferenceResponse response = response();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        boolean handled = RunResponseContentRenderer.renderStructured(
                response, Map.of(), true, false, null, null,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertTrue(handled);
        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"text\" : \"hello\""));
        assertTrue(json.contains("\"tokenCount\" : 5"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void leavesOrdinaryTextModeForTheCommandToContinue() {
        boolean handled = RunResponseContentRenderer.renderStructured(
                response(), Map.of(), false, false, null, null,
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertFalse(handled);
    }

    @Test
    void rendersDecodedTextAndSessionWithoutCommandState() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        RunResponseContentRenderer.renderText(
                response(), false, false,
                new PrintStream(stdout, true, StandardCharsets.UTF_8));

        String output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("hello"));
        assertTrue(output.contains("Session: session-1"));
    }

    private static InferenceResponse response() {
        return InferenceResponse.builder()
                .requestId("req-1")
                .model("demo")
                .content("hello")
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tokensUsed(5)
                .inputTokens(2)
                .durationMs(10)
                .sessionId("session-1")
                .build();
    }
}
