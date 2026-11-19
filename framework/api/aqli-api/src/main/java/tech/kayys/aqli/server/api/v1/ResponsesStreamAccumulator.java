package tech.kayys.aqli.server.api.v1;

import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the per-request state needed to produce a complete Responses stream.
 *
 * <p>All mutable state is scoped to one stream subscription. Wire formatting remains
 * in {@link ResponsesApiSerializer}; this class owns ordering and aggregation only.</p>
 */
final class ResponsesStreamAccumulator {
    private final String requestId;
    private final String responseId;
    private final String messageId;
    private final String model;
    private final AgentTraceContext trace;
    private final AgentStreamOptions options;
    private final StringBuilder outputText = new StringBuilder();
    private final Map<String, FunctionCallState> functionCalls = new LinkedHashMap<>();
    private long sequence;
    private boolean terminal;

    ResponsesStreamAccumulator(
            String requestId, String model, AgentTraceContext trace, AgentStreamOptions options) {
        this.requestId = requestId != null ? requestId : trace.requestId();
        this.responseId = "resp_" + this.requestId;
        this.messageId = "msg_" + this.requestId;
        this.model = model;
        this.trace = trace;
        this.options = options;
    }

    List<String> accept(TextGenerationChunk chunk) {
        if (terminal) throw new IllegalStateException("Responses stream is already terminal");
        List<String> events = new ArrayList<>();
        if (!chunk.delta().isEmpty()) {
            outputText.append(chunk.delta());
            Map<String, Object> delta = event("response.output_text.delta", 0);
            delta.put("item_id", messageId);
            delta.put("content_index", 0);
            delta.put("delta", chunk.delta());
            events.add(ResponsesApiSerializer.json(delta));
        }
        if (chunk.toolCallEvent() != null) appendToolEvent(chunk, events);
        if (chunk.terminal()) appendTerminal(chunk, events);
        return events;
    }

    private void appendToolEvent(TextGenerationChunk chunk, List<String> events) {
        String callId = chunk.toolCallId();
        FunctionCallState call;
        switch (chunk.toolCallEvent()) {
            case START -> {
                if (functionCalls.containsKey(callId)) {
                    throw new IllegalStateException("Duplicate function call start: " + callId);
                }
                call = new FunctionCallState(callId, chunk.toolName(), functionCalls.size() + 1);
                functionCalls.put(callId, call);
                Map<String, Object> added = event("response.output_item.added", call.outputIndex);
                added.put("item", Map.of(
                        "id", callId, "type", "function_call", "call_id", callId,
                        "name", call.name, "arguments", "", "status", "in_progress"));
                events.add(ResponsesApiSerializer.json(added));
            }
            case DELTA -> {
                call = requireCall(callId);
                if (call.completed) throw new IllegalStateException("Function call is already complete: " + callId);
                call.arguments.append(chunk.toolInputDelta());
                Map<String, Object> delta = event("response.function_call_arguments.delta", call.outputIndex);
                delta.put("item_id", callId);
                delta.put("call_id", callId);
                delta.put("delta", chunk.toolInputDelta());
                events.add(ResponsesApiSerializer.json(delta));
            }
            case END -> {
                call = requireCall(callId);
                call.completed = true;
                Map<String, Object> done = event("response.function_call_arguments.done", call.outputIndex);
                done.put("item_id", callId);
                done.put("call_id", callId);
                done.put("arguments", call.arguments.toString());
                events.add(ResponsesApiSerializer.json(done));
                appendItemDone(call, events);
            }
        }
    }

    private void appendTerminal(TextGenerationChunk chunk, List<String> events) {
        Map<String, Object> textDone = event("response.output_text.done", 0);
        textDone.put("item_id", messageId);
        textDone.put("content_index", 0);
        textDone.put("text", outputText.toString());
        ResponsesApiSerializer.metadata(textDone, trace, options, sequence, true, chunk.finishReason());
        events.add(ResponsesApiSerializer.json(textDone));

        for (FunctionCallState call : functionCalls.values()) {
            if (!call.completed) {
                call.completed = true;
                appendItemDone(call, events);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", responseId);
        response.put("object", "response");
        response.put("created_at", chunk.emittedAt().getEpochSecond());
        response.put("status", "completed");
        response.put("model", model);
        List<Map<String, Object>> output = new ArrayList<>();
        output.add(ResponsesApiSerializer.messageItem(requestId, outputText.toString()));
        for (FunctionCallState call : functionCalls.values()) {
            output.add(ResponsesApiSerializer.functionCallItem(
                    call.id, call.id, call.name, call.arguments.toString()));
        }
        response.put("output", output);
        response.put("output_text", outputText.toString());
        if (options.includeUsage() && chunk.usage() != null) {
            response.put("usage", Map.of(
                    "input_tokens", chunk.usage().inputTokens(),
                    "output_tokens", chunk.usage().outputTokens(),
                    "total_tokens", chunk.usage().inputTokens() + chunk.usage().outputTokens()));
        }
        ResponsesApiSerializer.metadata(response, trace, options, sequence + 1, true, chunk.finishReason());
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("type", "response.completed");
        completed.put("sequence_number", ++sequence);
        completed.put("response", response);
        events.add(ResponsesApiSerializer.json(completed));
        terminal = true;
    }

    private void appendItemDone(FunctionCallState call, List<String> events) {
        Map<String, Object> itemDone = event("response.output_item.done", call.outputIndex);
        itemDone.put("item", ResponsesApiSerializer.functionCallItem(
                call.id, call.id, call.name, call.arguments.toString()));
        events.add(ResponsesApiSerializer.json(itemDone));
    }

    private Map<String, Object> event(String type, int outputIndex) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("sequence_number", ++sequence);
        event.put("response_id", responseId);
        event.put("output_index", outputIndex);
        ResponsesApiSerializer.metadata(event, trace, options, sequence, false, null);
        return event;
    }

    private FunctionCallState requireCall(String callId) {
        FunctionCallState call = functionCalls.get(callId);
        if (call == null) throw new IllegalStateException("Function call has not started: " + callId);
        return call;
    }

    private static final class FunctionCallState {
        private final String id;
        private final String name;
        private final int outputIndex;
        private final StringBuilder arguments = new StringBuilder();
        private boolean completed;

        private FunctionCallState(String id, String name, int outputIndex) {
            this.id = id;
            this.name = name;
            this.outputIndex = outputIndex;
        }
    }
}
