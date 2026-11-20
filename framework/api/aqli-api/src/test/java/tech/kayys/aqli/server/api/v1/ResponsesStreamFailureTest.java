package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesStreamFailureTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentTraceContext trace =
            AgentTraceContext.fromPayload(mapper.createObjectNode().put("request_id", "req"));
    private final AgentStreamOptions options = new AgentStreamOptions(true, true, true);

    @Test
    void failureEmitsSingleTerminalResponseFailedWithPartialOutput() throws Exception {
        ResponsesStreamAccumulator accumulator = accumulator();
        accumulator.accept(chunk("partial output", false));
        String serialized = accumulator.fail(new IllegalStateException("backend unavailable")).getFirst();
        JsonNode event = mapper.readTree(serialized);

        assertEquals("response.failed", event.path("type").asText());
        assertEquals("failed", event.path("response").path("status").asText());
        assertEquals("partial output", event.path("response").path("output_text").asText());
        assertEquals("backend unavailable", event.path("error").path("message").asText());
        assertTrue(accumulator.fail(new IllegalStateException("second failure")).isEmpty());
        assertThrows(IllegalStateException.class, () -> accumulator.accept(chunk("late", false)));
    }

    @Test
    void cancellationDoesNotInventCompletionOrFailure() {
        ResponsesStreamAccumulator accumulator = accumulator();
        accumulator.accept(chunk("partial", false));
        accumulator.cancel();

        assertThrows(IllegalStateException.class, () -> accumulator.accept(chunk("late", false)));
        assertTrue(accumulator.fail(new IllegalStateException("late failure")).isEmpty());
    }

    private ResponsesStreamAccumulator accumulator() {
        return new ResponsesStreamAccumulator("req", "demo", trace, options);
    }

    private TextGenerationChunk chunk(String delta, boolean terminal) {
        return new TextGenerationChunk("req", 0, delta, terminal,
                terminal ? TextGenerationResponse.FinishReason.STOP : null,
                null, null, null, null, null, Instant.parse("2026-11-19T10:00:00Z"), Map.of());
    }
}
