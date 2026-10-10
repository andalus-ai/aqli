package tech.kayys.aqli.protobuf;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.aqli.sdk.api.AqliSdk;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.Message;

import java.util.stream.Collectors;

@GrpcService
public class AqliGrpcService implements AqliService {

    private static final Logger LOG = LoggerFactory.getLogger(AqliGrpcService.class);

    private AqliSdk sdk;

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    void init() {
        sdk = AqliSdk.builder().provider("local").build();
    }

    @Override
    public Uni<ChatResponse> chat(ChatRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received chat request for model: {}", request.getModelId());
        
        return Uni.createFrom().item(() -> {
            InferenceRequest internalRequest = InferenceRequest.builder()
                    .model(request.getModelId())
                    .messages(request.getMessagesList().stream()
                            .map(m -> new Message(Message.Role.valueOf(m.getRole().toUpperCase()), m.getContent()))
                            .collect(Collectors.toList()))
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .build();
            
            InferenceResponse response = sdk.createCompletion(internalRequest);
            
            sample.stop(meterRegistry.timer("aqli.grpc.chat.duration"));
            meterRegistry.counter("aqli.grpc.chat.count").increment();
            if (response.getInputTokens() > 0 || response.getOutputTokens() > 0) {
                meterRegistry.counter("aqli.grpc.chat.tokens").increment(response.getInputTokens() + response.getOutputTokens());
            }
            
            LOG.info("Chat response completed for model: {}, finish reason: {}", request.getModelId(), response.getFinishReason());
            return mapChatResponse(response);
        }).onFailure().invoke(th -> {
            LOG.error("Failed chat request for model: {}", request.getModelId(), th);
            meterRegistry.counter("aqli.grpc.chat.error").increment();
        });
    }

    @Override
    public Multi<ChatResponse> streamChat(ChatRequest request) {
        LOG.info("Received streamChat request for model: {}", request.getModelId());
        throw new UnsupportedOperationException("Streaming not implemented via gRPC mutiny currently");
    }

    @Override
    public Uni<CompletionResponse> complete(CompletionRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received complete request for model: {}", request.getModelId());
        
        return Uni.createFrom().item(() -> {
            InferenceRequest internalRequest = InferenceRequest.builder()
                    .model(request.getModelId())
                    .prompt(request.getPrompt())
                    .message(new Message(Message.Role.USER, request.getPrompt()))
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .build();
            
            InferenceResponse response = sdk.createCompletion(internalRequest);
            
            sample.stop(meterRegistry.timer("aqli.grpc.complete.duration"));
            meterRegistry.counter("aqli.grpc.complete.count").increment();
            if (response.getInputTokens() > 0 || response.getOutputTokens() > 0) {
                meterRegistry.counter("aqli.grpc.complete.tokens").increment(response.getInputTokens() + response.getOutputTokens());
            }
            
            LOG.info("Complete response completed for model: {}, finish reason: {}", request.getModelId(), response.getFinishReason());
            return mapCompletionResponse(response);
        }).onFailure().invoke(th -> {
            LOG.error("Failed complete request for model: {}", request.getModelId(), th);
            meterRegistry.counter("aqli.grpc.complete.error").increment();
        });
    }

    @Override
    public Multi<CompletionResponse> streamComplete(CompletionRequest request) {
        LOG.info("Received streamComplete request for model: {}", request.getModelId());
        throw new UnsupportedOperationException("Streaming not implemented via gRPC mutiny currently");
    }

    private ChatResponse mapChatResponse(InferenceResponse response) {
        return ChatResponse.newBuilder()
                .setId(response.getRequestId())
                .setMessage(tech.kayys.aqli.protobuf.Message.newBuilder()
                        .setRole("assistant")
                        .setContent(response.getContent())
                        .build())
                .setFinishReason(response.getFinishReason() != null ? response.getFinishReason().name() : "STOP")
                .setCompletionTokens(response.getOutputTokens())
                .build();
    }

    private CompletionResponse mapCompletionResponse(InferenceResponse response) {
        return CompletionResponse.newBuilder()
                .setId(response.getRequestId())
                .setText(response.getContent())
                .setFinishReason(response.getFinishReason() != null ? response.getFinishReason().name() : "STOP")
                .setCompletionTokens(response.getOutputTokens())
                .build();
    }
}
