package tech.kayys.aqli.sdk.core;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationToolDefinition;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared chat service facade that abstracts away the construction of
 * InferenceRequests, allowing a unified multi-turn chat and tool-use
 * experience across CLI, REST API, gRPC, and embedded environments (Wayang).
 */
public final class AqliChatService {
    private final SdkObservabilityProvider obs;

    public AqliChatService() {
        this.obs = SdkObservabilityProvider.noop();
    }

    public AqliChatService(SdkObservabilityProvider obs) {
        this.obs = obs != null ? obs : SdkObservabilityProvider.noop();
    }

    /**
     * Builds the canonical public SDK text request used by transport adapters.
     * Transport-specific DTOs must be normalized to Message before reaching this method.
     */
    public TextGenerationRequest createTextGenerationRequest(
            String modelId,
            String systemPrompt,
            List<Message> history,
            List<ToolDefinition> tools,
            ChatParams params) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }
        if (params == null) {
            params = ChatParams.defaults();
        }
        TextGenerationParameters parameters = new TextGenerationParameters(
                params.temperature(),
                params.topP(),
                40,
                params.maxTokens() > 0 ? params.maxTokens() : null,
                params.repeatPenalty(),
                null,
                null,
                false,
                null,
                List.of());
        List<TextGenerationMessage> sdkMessages = messages.stream()
                .map(message -> new TextGenerationMessage(
                        TextGenerationMessage.Role.valueOf(message.getRole().name()),
                        message.getContent() == null ? "" : message.getContent(),
                        message.getName(),
                        message.getToolCallId()))
                .toList();
        List<TextGenerationToolDefinition> sdkTools = tools == null ? List.of() : tools.stream()
                .map(tool -> new TextGenerationToolDefinition(
                        tool.getName(),
                        TextGenerationToolDefinition.Type.valueOf(tool.getType().name()),
                        tool.getDescription().orElse(null),
                        tool.getParameters(),
                        tool.isStrict(),
                        tool.getMetadata()))
                .toList();
        return new TextGenerationRequest(modelId, sdkMessages, parameters, sdkTools);
    }

    /**
     * Executes the canonical typed text capability and applies shared SDK observability.
     */
    public Uni<TextGenerationResponse> generateText(
            AqliRuntime sdk,
            TextGenerationRequest request) {
        String requestId = resolveRequestId(request);
        TextGenerationRequest effectiveRequest = withRequestId(request, requestId);
        String modelId = effectiveRequest.model();
        recordAudit(audit -> audit.logClientRequestStart(requestId, modelId));
        long startMs = System.currentTimeMillis();

        return Uni.createFrom().completionStage(() -> sdk.generateTextAsync(effectiveRequest))
                .onItem().invoke(response -> {
                    long duration = System.currentTimeMillis() - startMs;
                    recordMetrics(metrics -> metrics.recordClientSuccess(modelId, duration, response.totalTokens()));
                    recordAudit(audit -> audit.logClientRequestComplete(requestId, modelId, response.totalTokens(), duration));
                })
                .onFailure().invoke(error -> {
                    recordMetrics(metrics -> metrics.recordClientFailure(modelId, error.getClass().getSimpleName()));
                    recordAudit(audit -> audit.logClientRequestFailure(
                            requestId, modelId, error.getClass().getSimpleName()));
                });
    }


    /**
     * Streams the canonical typed text capability while retaining the legacy
     * StreamingInferenceChunk shape consumed by existing REST and gRPC clients.
     *
     * <p>The JDK Flow publisher is adapted in the Flow-to-Reactive-Streams
     * direction. Mutiny owns the downstream subscription, so cancellation is
     * propagated to the SDK publisher when the transport cancels.</p>
     */
    public Multi<StreamingInferenceChunk> streamText(
            AqliRuntime sdk,
            TextGenerationRequest request) {
        String requestId = resolveRequestId(request);
        TextGenerationRequest effectiveRequest = withRequestId(request, requestId);
        String modelId = effectiveRequest.model();
        return Multi.createFrom().deferred(() -> {
            StreamingObservation observation = new StreamingObservation(requestId, modelId);
            observation.start();

            return Multi.createFrom().publisher(sdk.streamText(effectiveRequest))
                    .onItem().invoke(sourceChunk -> {
                        TextGenerationChunk.Usage usage = sourceChunk.usage();
                        if (usage != null) {
                            // Usage is a snapshot; replace rather than accumulate across chunks.
                            observation.updateOutputTokens((int) usage.outputTokens());
                        }
                    })
                    .map(AqliChatService::toStreamingInferenceChunk)
                    .onItem().invoke(chunk -> observation.onChunk(
                            hasOutput(chunk.delta(), chunk.toolInputDelta())))
                    .onCompletion().invoke(observation::complete)
                    .onFailure().invoke(observation::fail)
                    .onCancellation().invoke(observation::cancel);
        });
    }

    /**
     * Per-subscription lifecycle shared by typed and legacy text streams.
     * Request contracts and chunk adaptation stay at their respective call sites.
     */
    private final class StreamingObservation {
        private final String requestId;
        private final String modelId;
        private final long startMs = System.currentTimeMillis();
        private final java.util.concurrent.atomic.AtomicBoolean firstOutputObserved =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicInteger outputTokens =
                new java.util.concurrent.atomic.AtomicInteger();

        private StreamingObservation(String requestId, String modelId) {
            this.requestId = requestId;
            this.modelId = modelId;
        }

        private void start() {
            recordAudit(audit -> audit.logClientRequestStart(requestId, modelId));
        }

        private void updateOutputTokens(int value) {
            outputTokens.set(value);
        }

        private void onChunk(boolean hasOutput) {
            recordMetrics(metrics -> metrics.recordClientChunk(modelId));
            if (hasOutput && firstOutputObserved.compareAndSet(false, true)) {
                recordMetrics(metrics -> metrics.recordClientTtft(
                        modelId, System.currentTimeMillis() - startMs));
            }
        }

        private void complete() {
            long duration = System.currentTimeMillis() - startMs;
            recordAudit(audit -> audit.logClientRequestComplete(
                    requestId, modelId, outputTokens.get(), duration));
        }

        private void fail(Throwable error) {
            recordMetrics(metrics -> metrics.recordClientFailure(
                    modelId, error.getClass().getSimpleName()));
            recordAudit(audit -> audit.logClientRequestFailure(
                    requestId, modelId, error.getClass().getSimpleName()));
        }

        private void cancel() {
            long duration = System.currentTimeMillis() - startMs;
            recordMetrics(metrics -> metrics.recordClientCancellation(modelId, duration));
            recordAudit(audit -> audit.logClientRequestCancelled(requestId, modelId, duration));
        }
    }

    private static boolean hasOutput(String delta, String toolInputDelta) {
        return (delta != null && !delta.isEmpty())
                || (toolInputDelta != null && !toolInputDelta.isEmpty());
    }

    /**
     * Observability is an optional side channel: a faulty exporter must never
     * fail an inference request, interrupt a stream, or mask its original error.
     */
    private void recordMetrics(Consumer<tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector> action) {
        try {
            action.accept(obs.getMetricsCollector());
        } catch (RuntimeException ignored) {
            // Deliberately isolate instrumentation failures from inference execution.
        }
    }

    private void recordAudit(Consumer<tech.kayys.aqli.sdk.core.observability.SdkAuditLogger> action) {
        try {
            action.accept(obs.getAuditLogger());
        } catch (RuntimeException ignored) {
            // Deliberately isolate instrumentation failures from inference execution.
        }
    }

    private static StreamingInferenceChunk toStreamingInferenceChunk(TextGenerationChunk chunk) {
        TextGenerationChunk.Usage sourceUsage = chunk.usage();
        StreamingInferenceChunk.ChunkUsage usage = sourceUsage == null ? null
                : new StreamingInferenceChunk.ChunkUsage(
                        sourceUsage.inputTokens(), sourceUsage.outputTokens(), sourceUsage.latencyMs());
        String finishReason = chunk.finishReason() == null ? null
                : chunk.finishReason().name().toLowerCase(java.util.Locale.ROOT);
        return new StreamingInferenceChunk(
                chunk.requestId(),
                Math.toIntExact(chunk.sequence()),
                tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                chunk.delta(),
                null,
                chunk.terminal(),
                finishReason,
                usage,
                chunk.emittedAt(),
                chunk.metadata(),
                chunk.toolCallId(),
                chunk.toolName(),
                chunk.toolInputDelta());
    }

    private static String resolveRequestId(TextGenerationRequest request) {
        return request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId();
    }

    private static TextGenerationRequest withRequestId(TextGenerationRequest request, String requestId) {
        if (requestId.equals(request.requestId())) {
            return request;
        }
        return new TextGenerationRequest(
                request.model(),
                request.messages(),
                request.parameters(),
                request.tools(),
                request.toolChoice(),
                request.metadata(),
                requestId,
                request.userId(),
                request.sessionId(),
                request.traceId(),
                request.apiKey(),
                request.context());
    }

    public Multi<StreamingInferenceChunk> streamChat(
            AqliRuntime sdk,
            String modelId,
            String systemPrompt,
            List<Message> history,
            List<ToolDefinition> tools,
            ChatParams p) {

        InferenceRequest req = createRequest(modelId, systemPrompt, history, tools, p, true);
        return Multi.createFrom().deferred(() -> {
            StreamingObservation observation = new StreamingObservation(req.getRequestId(), modelId);
            observation.start();

            return sdk.streamCompletion(req)
                    .onItem().invoke(chunk -> {
                        if (chunk.usage() != null) {
                            // Usage is a snapshot; replace rather than accumulate across chunks.
                            observation.updateOutputTokens(Math.toIntExact(chunk.usage().outputTokens()));
                        }
                        observation.onChunk(hasOutput(chunk.delta(), chunk.toolInputDelta()));
                    })
                    .onCompletion().invoke(observation::complete)
                    .onFailure().invoke(observation::fail)
                    .onCancellation().invoke(observation::cancel);
        });
    }

    public Uni<InferenceResponse> chat(
            AqliRuntime sdk,
            String modelId,
            String systemPrompt,
            List<Message> history,
            List<ToolDefinition> tools,
            ChatParams p) {

        InferenceRequest req = createRequest(modelId, systemPrompt, history, tools, p, false);
        recordAudit(audit -> audit.logClientRequestStart(req.getRequestId(), modelId));
        long startMs = System.currentTimeMillis();

        return Uni.createFrom().completionStage(() -> sdk.createCompletionAsync(req))
                .onItem().invoke(resp -> {
                    long duration = System.currentTimeMillis() - startMs;
                    recordMetrics(metrics -> metrics.recordClientSuccess(modelId, duration, resp.getTokensUsed()));
                    recordAudit(audit -> audit.logClientRequestComplete(req.getRequestId(), modelId, resp.getTokensUsed(), duration));
                })
                .onFailure().invoke(err -> {
                    recordMetrics(metrics -> metrics.recordClientFailure(modelId, err.getClass().getSimpleName()));
                    recordAudit(audit -> audit.logClientRequestFailure(req.getRequestId(), modelId, err.getClass().getSimpleName()));
                });
    }

    /**
     * Builds the canonical internal completion execution request used by transports.
     * Both the legacy prompt parameter and USER message are retained for runner
     * compatibility while callers migrate to message-based requests.
     */
    public InferenceRequest createCompletionRequest(
            String modelId,
            String prompt,
            ChatParams params,
            boolean streaming) {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .model(modelId)
                .prompt(prompt)
                .message(Message.user(prompt))
                .streaming(streaming);
        applyCommonParameters(builder, params);
        return builder.build();
    }

    /**
     * Builds the canonical internal chat execution request used by all transports.
     * Transport adapters remain responsible for translating their wire messages
     * into the shared {@link Message} representation.
     */
    public InferenceRequest createRequest(
            String modelId,
            String systemPrompt,
            List<Message> history,
            List<ToolDefinition> tools,
            ChatParams params,
            boolean streaming) {

        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }

        InferenceRequest.Builder request = InferenceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .model(modelId)
                .messages(messages)
                .streaming(streaming);
        applyCommonParameters(request, params);

        TextGenerationRequestMapper.apply(request, toTextGenerationOptions(params));
        if (tools != null && !tools.isEmpty()) {
            request.tools(tools);
        }

        return request.build();
    }

    private static TextGenerationOptions toTextGenerationOptions(ChatParams params) {
        return new TextGenerationOptions(
                params.temperature(),
                params.topP(),
                40,
                params.repeatPenalty(),
                false,
                params.maxTokens(),
                0,
                null);
    }

    /**
     * Applies shared sampling and provider settings to every chat request shape.
     */
    private static void applyCommonParameters(InferenceRequest.Builder builder, ChatParams params) {
        builder.preferredProvider(params.preferredProvider())
                .parameter("temperature", params.temperature())
                .parameter("max_tokens", params.maxTokens())
                .parameter("top_p", params.topP())
                .parameter("repeat_penalty", params.repeatPenalty());
    }
}
