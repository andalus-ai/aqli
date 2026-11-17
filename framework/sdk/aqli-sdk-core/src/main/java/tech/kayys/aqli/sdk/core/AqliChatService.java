package tech.kayys.aqli.sdk.core;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
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
        String requestId = UUID.randomUUID().toString();
        String modelId = request.model();
        obs.getAuditLogger().logClientRequestStart(requestId, modelId);
        long startMs = System.currentTimeMillis();

        return Uni.createFrom().completionStage(() -> sdk.generateTextAsync(request))
                .onItem().invoke(response -> {
                    long duration = System.currentTimeMillis() - startMs;
                    obs.getMetricsCollector().recordClientSuccess(modelId, duration, response.totalTokens());
                    obs.getAuditLogger().logClientRequestComplete(requestId, modelId, response.totalTokens(), duration);
                })
                .onFailure().invoke(error -> {
                    obs.getMetricsCollector().recordClientFailure(modelId, error.getClass().getSimpleName());
                    obs.getAuditLogger().logClientRequestFailure(
                            requestId, modelId, error.getClass().getSimpleName());
                });
    }

    public Multi<StreamingInferenceChunk> streamChat(
            AqliRuntime sdk,
            String modelId,
            String systemPrompt,
            List<Message> history,
            List<ToolDefinition> tools,
            ChatParams p) {

        InferenceRequest req = createRequest(modelId, systemPrompt, history, tools, p, true);
        obs.getAuditLogger().logClientRequestStart(req.getRequestId(), modelId);
        long startMs = System.currentTimeMillis();

        return sdk.streamCompletion(req)
                .onItem().invoke(chunk -> {
                    obs.getMetricsCollector().recordClientChunk(modelId);
                    // Approximation of TTFT for stream
                    if (chunk.delta() != null && !chunk.delta().isEmpty()) {
                        obs.getMetricsCollector().recordClientTtft(modelId, System.currentTimeMillis() - startMs);
                    }
                })
                .onCompletion().invoke(() -> {
                    long duration = System.currentTimeMillis() - startMs;
                    obs.getAuditLogger().logClientRequestComplete(req.getRequestId(), modelId, 0, duration);
                })
                .onFailure().invoke(err -> {
                    obs.getMetricsCollector().recordClientFailure(modelId, err.getClass().getSimpleName());
                    obs.getAuditLogger().logClientRequestFailure(req.getRequestId(), modelId, err.getClass().getSimpleName());
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
        obs.getAuditLogger().logClientRequestStart(req.getRequestId(), modelId);
        long startMs = System.currentTimeMillis();

        return Uni.createFrom().completionStage(() -> sdk.createCompletionAsync(req))
                .onItem().invoke(resp -> {
                    long duration = System.currentTimeMillis() - startMs;
                    obs.getMetricsCollector().recordClientSuccess(modelId, duration, resp.getTokensUsed());
                    obs.getAuditLogger().logClientRequestComplete(req.getRequestId(), modelId, resp.getTokensUsed(), duration);
                })
                .onFailure().invoke(err -> {
                    obs.getMetricsCollector().recordClientFailure(modelId, err.getClass().getSimpleName());
                    obs.getAuditLogger().logClientRequestFailure(req.getRequestId(), modelId, err.getClass().getSimpleName());
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
