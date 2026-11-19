package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.spi.tool.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializes transport-neutral SDK results into the Responses wire contract. */
final class ResponsesApiSerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponsesApiSerializer() {}

    static Map<String, Object> response(
            TextGenerationResponse result, TextGenerationRequest request, AgentTraceContext trace) {
        String requestId = request.requestId() != null ? request.requestId() : trace.requestId();
        String content = result.content() == null ? "" : result.content();
        List<Map<String, Object>> output = new ArrayList<>();
        output.add(messageItem(requestId, content));
        for (ToolCall call : result.toolCalls()) {
            output.add(functionCallItem(call));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", "resp_" + requestId);
        payload.put("object", "response");
        payload.put("created_at", Instant.now().getEpochSecond());
        payload.put("status", "completed");
        payload.put("model", result.model() != null ? result.model() : request.model());
        payload.put("output", output);
        payload.put("output_text", content);
        payload.put("usage", Map.of(
                "input_tokens", result.inputTokens(),
                "output_tokens", result.outputTokens(),
                "total_tokens", result.totalTokens()));
        payload.put("metadata", trace.withTraceMetadata(request.metadata()));
        return payload;
    }

    static String created(TextGenerationRequest request, AgentTraceContext trace, AgentStreamOptions options) {
        String requestId = request.requestId() != null ? request.requestId() : trace.requestId();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "resp_" + requestId);
        response.put("object", "response");
        response.put("created_at", Instant.now().getEpochSecond());
        response.put("status", "in_progress");
        response.put("model", request.model());
        response.put("output", List.of());
        response.put("output_text", "");
        metadata(response, trace, options, 0, false, null);
        return json(Map.of("type", "response.created", "sequence_number", 0, "response", response));
    }

    static List<String> chunk(
            TextGenerationChunk chunk, String fallbackModel, String outputText,
            AgentTraceContext trace, AgentStreamOptions options) {
        String requestId = chunk.requestId() != null ? chunk.requestId() : trace.requestId();
        String responseId = "resp_" + requestId;
        String messageId = "msg_" + requestId;
        List<String> events = new ArrayList<>();
        long sequence = chunk.sequence();
        if (!chunk.delta().isEmpty()) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "response.output_text.delta");
            long deltaSequence = ++sequence;
            delta.put("sequence_number", deltaSequence);
            delta.put("response_id", responseId);
            delta.put("item_id", messageId);
            delta.put("output_index", 0);
            delta.put("content_index", 0);
            delta.put("delta", chunk.delta());
            metadata(delta, trace, options, deltaSequence, false, null);
            events.add(json(delta));
        }
        if (chunk.toolCallEvent() != null) {
            Map<String, Object> toolEvent = new LinkedHashMap<>();
            long toolSequence = ++sequence;
            toolEvent.put("sequence_number", toolSequence);
            toolEvent.put("response_id", responseId);
            toolEvent.put("item_id", chunk.toolCallId());
            toolEvent.put("output_index", 1);
            switch (chunk.toolCallEvent()) {
                case START -> {
                    toolEvent.put("type", "response.output_item.added");
                    toolEvent.put("item", Map.of(
                            "id", chunk.toolCallId(), "type", "function_call",
                            "call_id", chunk.toolCallId(), "name", chunk.toolName(),
                            "arguments", "", "status", "in_progress"));
                }
                case DELTA -> {
                    toolEvent.put("type", "response.function_call_arguments.delta");
                    toolEvent.put("delta", chunk.toolInputDelta());
                    toolEvent.put("call_id", chunk.toolCallId());
                }
                case END -> {
                    toolEvent.put("type", "response.function_call_arguments.done");
                    toolEvent.put("call_id", chunk.toolCallId());
                }
            }
            metadata(toolEvent, trace, options, toolSequence, false, null);
            events.add(json(toolEvent));
            if (chunk.toolCallEvent() == TextGenerationChunk.ToolCallEvent.END) {
                Map<String, Object> itemDone = new LinkedHashMap<>();
                long itemDoneSequence = ++sequence;
                itemDone.put("type", "response.output_item.done");
                itemDone.put("sequence_number", itemDoneSequence);
                itemDone.put("response_id", responseId);
                itemDone.put("output_index", 1);
                itemDone.put("item", Map.of(
                        "id", chunk.toolCallId(), "type", "function_call",
                        "call_id", chunk.toolCallId(), "status", "completed"));
                metadata(itemDone, trace, options, itemDoneSequence, false, null);
                events.add(json(itemDone));
            }
        }
        if (chunk.terminal()) {
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("type", "response.output_text.done");
            long doneSequence = ++sequence;
            done.put("sequence_number", doneSequence);
            done.put("item_id", messageId);
            done.put("output_index", 0);
            done.put("content_index", 0);
            done.put("text", outputText);
            metadata(done, trace, options, doneSequence, true, chunk.finishReason());
            events.add(json(done));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", responseId);
            response.put("object", "response");
            response.put("created_at", chunk.emittedAt().getEpochSecond());
            response.put("status", "completed");
            response.put("model", fallbackModel);
            response.put("output", List.of(messageItem(requestId, outputText)));
            response.put("output_text", outputText);
            if (options.includeUsage() && chunk.usage() != null) {
                response.put("usage", Map.of(
                        "input_tokens", chunk.usage().inputTokens(),
                        "output_tokens", chunk.usage().outputTokens(),
                        "total_tokens", chunk.usage().inputTokens() + chunk.usage().outputTokens()));
            }
            long completedSequence = sequence + 1;
            metadata(response, trace, options, completedSequence, true, chunk.finishReason());
            events.add(json(Map.of(
                    "type", "response.completed",
                    "sequence_number", completedSequence,
                    "response", response)));
        }
        return events;
    }

    private static Map<String, Object> messageItem(String requestId, String content) {
        return Map.of(
                "id", "msg_" + requestId,
                "type", "message",
                "status", "completed",
                "role", "assistant",
                "content", List.of(Map.of(
                        "type", "output_text", "text", content, "annotations", List.of())));
    }

    private static Map<String, Object> functionCallItem(ToolCall call) {
        return Map.of(
                "id", call.getId(),
                "type", "function_call",
                "status", "completed",
                "call_id", call.getId(),
                "name", call.getFunction().getName(),
                "arguments", json(call.getArguments()));
    }

    private static void metadata(
            Map<String, Object> target, AgentTraceContext trace, AgentStreamOptions options,
            long sequence, boolean terminal, Object finishReason) {
        if (options.includeTrace()) target.put("trace", trace.asMap());
        if (options.includeStreamMetadata()) {
            Map<String, Object> metadata = new LinkedHashMap<>(options.summary("responses"));
            metadata.put("sequence_number", sequence);
            metadata.put("terminal", terminal);
            if (finishReason != null) metadata.put("finish_reason", finishReason.toString().toLowerCase());
            target.put("metadata", metadata);
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize Responses event", e);
        }
    }
}
