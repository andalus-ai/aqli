package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesStreamAccumulatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentTraceContext trace = AgentTraceContext.fromPayload(mapper.createObjectNode().put("request_id", "req"));
    private final AgentStreamOptions options = new AgentStreamOptions(true, true, true);

    @Test
    void accumulatesFunctionArgumentsIntoDoneAndCompletedItems() throws Exception {
        ResponsesStreamAccumulator accumulator = accumulator();
        List<JsonNode> events = acceptAll(accumulator, List.of(
                chunk(0, null, null, null, null, false, null),
                chunk(1, TextGenerationChunk.ToolCallEvent.START, "call-a", "lookup", null, false, null),
                chunk(2, TextGenerationChunk.ToolCallEvent.DELTA, "call-a", null, "{\"q\":", false, null),
                chunk(3, TextGenerationChunk.ToolCallEvent.DELTA, "call-a", null, "\"weather\"}", false, null),
                chunk(4, TextGenerationChunk.ToolCallEvent.END, "call-a", null, null, false, null),
                chunk(5, null, null, null, null, true, new TextGenerationChunk.Usage(4, 3, 12))));

        JsonNode itemDone = find(events, "response.output_item.done");
        assertEquals("lookup", itemDone.path("item").path("name").asText());
        assertEquals("{\"q\":\"weather\"}", itemDone.path("item").path("arguments").asText());

        JsonNode completed = find(events, "response.completed").path("response");
        assertEquals(2, completed.path("output").size());
        assertEquals("function_call", completed.path("output").get(1).path("type").asText());
        assertEquals("{\"q\":\"weather\"}", completed.path("output").get(1).path("arguments").asText());
        assertEquals(4, completed.path("usage").path("input_tokens").asInt());
    }

    @Test
    void preservesIndexesAndArgumentsForInterleavedCalls() throws Exception {
        ResponsesStreamAccumulator accumulator = accumulator();
        List<JsonNode> events = acceptAll(accumulator, List.of(
                chunk(0, TextGenerationChunk.ToolCallEvent.START, "call-a", "alpha", null, false, null),
                chunk(1, TextGenerationChunk.ToolCallEvent.START, "call-b", "beta", null, false, null),
                chunk(2, TextGenerationChunk.ToolCallEvent.DELTA, "call-b", null, "{\"b\":2}", false, null),
                chunk(3, TextGenerationChunk.ToolCallEvent.DELTA, "call-a", null, "{\"a\":1}", false, null),
                chunk(4, TextGenerationChunk.ToolCallEvent.END, "call-a", null, null, false, null),
                chunk(5, TextGenerationChunk.ToolCallEvent.END, "call-b", null, null, false, null),
                chunk(6, null, null, null, null, true, null)));

        JsonNode addedB = events.stream()
                .filter(e -> e.path("type").asText().equals("response.output_item.added"))
                .filter(e -> e.path("item").path("call_id").asText().equals("call-b"))
                .findFirst().orElseThrow();
        assertEquals(2, addedB.path("output_index").asInt());

        JsonNode output = find(events, "response.completed").path("response").path("output");
        assertEquals("call-a", output.get(1).path("call_id").asText());
        assertEquals("{\"a\":1}", output.get(1).path("arguments").asText());
        assertEquals("call-b", output.get(2).path("call_id").asText());
        assertEquals("{\"b\":2}", output.get(2).path("arguments").asText());
    }

    @Test
    void keepsTextAndToolOutputIndexesDistinctAndSequenceMonotonic() throws Exception {
        ResponsesStreamAccumulator accumulator = accumulator();
        List<JsonNode> events = acceptAll(accumulator, List.of(
                chunk(0, null, null, null, null, false, null, "hello "),
                chunk(1, TextGenerationChunk.ToolCallEvent.START, "call-a", "lookup", null, false, null),
                chunk(2, TextGenerationChunk.ToolCallEvent.DELTA, "call-a", null, "{}", false, null),
                chunk(3, null, null, null, null, true, null, "world")));

        assertEquals("hello ", find(events, "response.output_text.delta").path("delta").asText());
        assertEquals("world", events.stream()
                .filter(e -> e.path("type").asText().equals("response.output_text.delta"))
                .skip(1).findFirst().orElseThrow().path("delta").asText());
        assertEquals(0, events.stream()
                .filter(e -> e.path("type").asText().equals("response.output_text.delta"))
                .findFirst().orElseThrow().path("output_index").asInt());
        assertTrue(events.stream().mapToLong(e -> e.path("sequence_number").asLong())
                .distinct().count() == events.size());
        JsonNode output = find(events, "response.completed").path("response").path("output");
        assertEquals("message", output.get(0).path("type").asText());
        assertEquals("function_call", output.get(1).path("type").asText());
    }

    @Test
    void rejectsToolDeltasWithoutAStartedCall() {
        ResponsesStreamAccumulator accumulator = accumulator();
        assertThrows(IllegalStateException.class, () -> accumulator.accept(
                chunk(0, TextGenerationChunk.ToolCallEvent.DELTA, "missing", null, "{}", false, null)));
    }

    private ResponsesStreamAccumulator accumulator() {
        return new ResponsesStreamAccumulator("req", "demo", trace, options);
    }

    private List<JsonNode> acceptAll(ResponsesStreamAccumulator accumulator, List<TextGenerationChunk> chunks)
            throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (TextGenerationChunk chunk : chunks) {
            for (String event : accumulator.accept(chunk)) events.add(mapper.readTree(event));
        }
        return events;
    }

    private JsonNode find(List<JsonNode> events, String type) {
        return events.stream().filter(e -> e.path("type").asText().equals(type)).findFirst().orElseThrow();
    }

    private TextGenerationChunk chunk(long sequence, TextGenerationChunk.ToolCallEvent event,
            String callId, String name, String inputDelta, boolean terminal, TextGenerationChunk.Usage usage) {
        return chunk(sequence, event, callId, name, inputDelta, terminal, usage, "");
    }

    private TextGenerationChunk chunk(long sequence, TextGenerationChunk.ToolCallEvent event,
            String callId, String name, String inputDelta, boolean terminal,
            TextGenerationChunk.Usage usage, String text) {
        return new TextGenerationChunk("req", sequence, text, terminal,
                terminal ? TextGenerationResponse.FinishReason.TOOL_CALLS : null,
                event, callId, name, inputDelta, usage, Instant.parse("2026-11-19T10:00:00Z"), Map.of());
    }
}
