package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        for (ToolCall call : result.toolCalls()) output.add(functionCallItem(call));
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

    static Map<String, Object> messageItem(String requestId, String content) {
        return Map.of(
                "id", "msg_" + requestId,
                "type", "message",
                "status", "completed",
                "role", "assistant",
                "content", List.of(Map.of(
                        "type", "output_text", "text", content, "annotations", List.of())));
    }

    static Map<String, Object> functionCallItem(ToolCall call) {
        return functionCallItem(call.getId(), call.getId(), call.getFunction().getName(),
                json(call.getArguments()));
    }

    static Map<String, Object> functionCallItem(String itemId, String callId, String name, String arguments) {
        return Map.of(
                "id", itemId,
                "type", "function_call",
                "status", "completed",
                "call_id", callId,
                "name", name,
                "arguments", arguments);
    }

    static void metadata(
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

    static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize Responses event", e);
        }
    }
}
