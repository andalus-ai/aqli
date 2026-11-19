package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesApiSerializerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesTextDeltaAndTerminalLifecycleWithMonotonicSequences() throws Exception {
        AgentTraceContext trace = AgentTraceContext.fromPayload(mapper.readTree("""
                {"request_id":"req-1","trace_id":"trace-1"}
                """));
        AgentStreamOptions options = new AgentStreamOptions(true, true, true);

        TextGenerationChunk delta = new TextGenerationChunk(
                "req-1", 0, "hello", false, null, null,
                null, null, null, null, Instant.parse("2026-11-19T10:00:00Z"), Map.of());
        TextGenerationChunk terminal = new TextGenerationChunk(
                "req-1", 1, "", true, TextGenerationResponse.FinishReason.STOP, null,
                null, null, null, new TextGenerationChunk.Usage(2, 1, 15),
                Instant.parse("2026-11-19T10:00:01Z"), Map.of());

        List<String> deltaEvents = ResponsesApiSerializer.chunk(delta, "demo", "hello", trace, options);
        List<String> terminalEvents = ResponsesApiSerializer.chunk(terminal, "demo", "hello", trace, options);
        assertEquals(1, deltaEvents.size());
        assertEquals(2, terminalEvents.size());

        JsonNode deltaJson = mapper.readTree(deltaEvents.get(0));
        JsonNode doneJson = mapper.readTree(terminalEvents.get(0));
        JsonNode completedJson = mapper.readTree(terminalEvents.get(1));

        assertEquals("response.output_text.delta", deltaJson.path("type").asText());
        assertEquals("hello", deltaJson.path("delta").asText());
        assertEquals("response.output_text.done", doneJson.path("type").asText());
        assertEquals("hello", doneJson.path("text").asText());
        assertEquals("response.completed", completedJson.path("type").asText());
        assertEquals(2, completedJson.path("response").path("usage").path("input_tokens").asInt());
        assertEquals(1, completedJson.path("response").path("usage").path("output_tokens").asInt());
        assertTrue(completedJson.path("response").path("trace").has("trace_id"));

        long first = deltaJson.path("sequence_number").asLong();
        long second = doneJson.path("sequence_number").asLong();
        long third = completedJson.path("sequence_number").asLong();
        assertTrue(first < second && second < third);
        assertEquals(second, doneJson.path("metadata").path("sequence_number").asLong());
        assertEquals(third, completedJson.path("response").path("metadata").path("sequence_number").asLong());
    }

    @Test
    void serializesToolCallStartDeltaAndEndAsResponsesEvents() throws Exception {
        AgentTraceContext trace = AgentTraceContext.fromPayload(mapper.readTree("""
                {"request_id":"req-tool"}
                """));
        AgentStreamOptions options = new AgentStreamOptions(false, false, false);

        TextGenerationChunk start = new TextGenerationChunk(
                "req-tool", 0, "", false, null, TextGenerationChunk.ToolCallEvent.START,
                "call-1", "lookup", null, null, Instant.now(), Map.of());
        TextGenerationChunk delta = new TextGenerationChunk(
                "req-tool", 1, "", false, null, TextGenerationChunk.ToolCallEvent.DELTA,
                "call-1", null, "{\\"q\\":1}", null, Instant.now(), Map.of());
        TextGenerationChunk end = new TextGenerationChunk(
                "req-tool", 2, "", false, null, TextGenerationChunk.ToolCallEvent.END,
                "call-1", null, null, null, Instant.now(), Map.of());

        JsonNode startEvent = mapper.readTree(ResponsesApiSerializer.chunk(start, "demo", "", trace, options).get(0));
        JsonNode deltaEvent = mapper.readTree(ResponsesApiSerializer.chunk(delta, "demo", "", trace, options).get(0));
        JsonNode endEvent = mapper.readTree(ResponsesApiSerializer.chunk(end, "demo", "", trace, options).get(0));

        assertEquals("response.output_item.added", startEvent.path("type").asText());
        assertEquals("function_call", startEvent.path("item").path("type").asText());
        assertEquals("response.function_call_arguments.delta", deltaEvent.path("type").asText());
        assertEquals("{\\"q\\":1}", deltaEvent.path("delta").asText());
        assertEquals("response.function_call_arguments.done", endEvent.path("type").asText());
        assertFalse(startEvent.has("trace"));
    }
}
