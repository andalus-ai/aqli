package tech.kayys.aqli.server.api.v1;

import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates one Responses stream; serialization remains in {@link ResponsesApiSerializer}. */
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
    private boolean cancelled;

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
        ensureActive();
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

    /** Completes a failed response with the output accumulated before the upstream error. */
    List<String> fail(Throwable failure) {
        if (terminal || cancelled) return List.of();
        terminal = true;
        String message = failure != null && failure.getMessage() != null
                ? failure.getMessage() : "Streaming response failed";
        Map<String, Object> error = AgentErrorMapper.errorObject(
                message, AgentErrorMapper.SERVER_ERROR, trace);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", responseId);
        response.put("object", "response");
        response.put("created_at", Instant.now().getEpochSecond());
        response.put("status", "failed");
        response.put("model", model);
        List<Map<String, Object>> output = new ArrayList<>();
        output.add(ResponsesApiSerializer.messageItem(requestId, outputText.toString()));
        for (FunctionCallState call : functionCalls.values()) {
            output.add(functionCallItem(call, call.completed ? "completed" : "in_progress"));
        }
        response.put("output", output);
        response.put("output_text", outputText.toString());
        response.put("error", error);
        ResponsesApiSerializer.metadata(response, trace, options, sequence + 1, true, "error");

        Map<String, Object> failed = new LinkedHashMap<>();
        failed.put("type", "response.failed");
        failed.put("sequence_number", ++sequence);
        failed.put("response", response);
        failed.put("error", error);
        return List.of(ResponsesApiSerializer.json(failed));
    }

    /** Cancellation is terminal locally; a cancelled subscriber cannot receive another SSE event. */
    void cancel() {
        if (!terminal) cancelled = true;
    }

    private void appendToolEvent(TextGenerationChunk chunk, List<String> events) {
        String callId = chunk.toolCallId();
        FunctionCallState call;
        switch (chunk.toolCallEvent()) {
            case START -> {
                if (callId == null || callId.isBlank() || chunk.toolName() == null || chunk.toolName().isBlank()) {
                    throw new IllegalStateException("Function call start requires an id and name");
                }
                if (functionCalls.containsKey(callId)) {
                    throw new IllegalStateException("Duplicate function call start: " + callId);
                }
                call = new FunctionCallState(callId, chunk.toolName(), functionCalls.size() + 1);
                functionCalls.put(callId, call);
                Map<String, Object> added = event("response.output_item.added", call.outputIndex);
                added.put("item", functionCallItem(call, "in_progress"));
                events.add(ResponsesApiSerializer.json(added));
            }
            case DELTA -> {
                call = requireCall(callId);
                if (call.completed) throw new IllegalStateException("Function call is already complete: " + callId);
                if (chunk.toolInputDelta() != null) call.arguments.append(chunk.toolInputDelta());
                Map<String, Object> delta = event("response.function_call_arguments.delta", call.outputIndex);
                delta.put("item_id", call.id);
                delta.put("call_id", call.id);
                delta.put("delta", chunk.toolInputDelta() == null ? "" : chunk.toolInputDelta());
                events.add(ResponsesApiSerializer.json(delta));
            }
            case END -> {
                call = requireCall(callId);
                if (call.completed) throw new IllegalStateException("Function call is already complete: " + callId);
                call.completed = true;
                Map<String, Object> done = event("response.function_call_arguments.done", call.outputIndex);
                done.put("item_id", call.id);
                done.put("call_id", call.id);
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
        for (FunctionCallState call : functionCalls.values()) output.add(functionCallItem(call, "completed"));
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
        itemDone.put("item", functionCallItem(call, "completed"));
        events.add(ResponsesApiSerializer.json(itemDone));
    }

    private Map<String, Object> functionCallItem(FunctionCallState call, String status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", call.id);
        item.put("type", "function_call");
        item.put("status", status);
        item.put("call_id", call.id);
        item.put("name", call.name);
        item.put("arguments", call.arguments.toString());
        return item;
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

    private void ensureActive() {
        if (cancelled) throw new IllegalStateException("Responses stream was cancelled");
        if (terminal) throw new IllegalStateException("Responses stream is already terminal");
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
