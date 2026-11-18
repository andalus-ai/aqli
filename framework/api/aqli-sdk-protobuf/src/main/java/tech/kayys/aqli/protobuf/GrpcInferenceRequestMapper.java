package tech.kayys.aqli.protobuf;

import tech.kayys.aqli.sdk.core.AqliChatService;
import tech.kayys.aqli.sdk.core.ChatParams;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.List;
import java.util.Locale;

/**
 * Translates the gRPC wire DTOs into the shared SDK request model.
 *
 * <p>This class owns transport conversion only. Request semantics and default
 * sampling behavior remain in {@link AqliChatService}.</p>
 */
public final class GrpcInferenceRequestMapper {

    private final AqliChatService chatService;

    public GrpcInferenceRequestMapper() {
        this(new AqliChatService());
    }

    GrpcInferenceRequestMapper(AqliChatService chatService) {
        this.chatService = chatService;
    }


    /**
     * Converts the gRPC chat payload into the canonical public SDK request.
     * Protobuf fields are translated here; defaults and request semantics are
     * applied by the shared chat service.
     */
    public tech.kayys.aqli.sdk.capability.text.TextGenerationRequest toTextGenerationRequest(
            ChatRequest request) {
        List<tech.kayys.aqli.spi.Message> messages = request.getMessagesList().stream()
                .map(message -> new tech.kayys.aqli.spi.Message(
                        tech.kayys.aqli.spi.Message.Role.valueOf(message.getRole().toUpperCase(Locale.ROOT)),
                        message.getContent()))
                .toList();
        return chatService.createTextGenerationRequest(
                request.getModelId(),
                null,
                messages,
                List.of(),
                ChatParams.of(request.getTemperature(), request.getMaxTokens()));
    }

    public InferenceRequest toChatRequest(ChatRequest request, boolean streaming) {
        List<tech.kayys.aqli.spi.Message> messages = request.getMessagesList().stream()
                .map(message -> new tech.kayys.aqli.spi.Message(
                        tech.kayys.aqli.spi.Message.Role.valueOf(message.getRole().toUpperCase(Locale.ROOT)),
                        message.getContent()))
                .toList();

        return chatService.createRequest(
                request.getModelId(),
                null,
                messages,
                List.of(),
                ChatParams.of(request.getTemperature(), request.getMaxTokens()),
                streaming);
    }

    public InferenceRequest toCompletionRequest(CompletionRequest request, boolean streaming) {
        return chatService.createCompletionRequest(
                request.getModelId(),
                request.getPrompt(),
                ChatParams.of(request.getTemperature(), request.getMaxTokens()),
                streaming);
    }
}
