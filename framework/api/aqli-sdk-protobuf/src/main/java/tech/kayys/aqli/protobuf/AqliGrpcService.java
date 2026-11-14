package tech.kayys.aqli.protobuf;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.grpc.GrpcService;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.sdk.core.AqliChatService;
import tech.kayys.aqli.sdk.core.ChatParams;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.Message;

import java.util.concurrent.CompletionException;
import java.util.List;

@GrpcService
public class AqliGrpcService extends AqliServiceGrpc.AqliServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(AqliGrpcService.class);

    @Inject
    AqliRuntime sdk;

    @Inject
    MeterRegistry meterRegistry;

    private final AqliChatService chatService = new AqliChatService();

    @Override
    public void chat(ChatRequest request, StreamObserver<ChatResponse> observer) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received chat request for model: {}", request.getModelId());
        
        Uni<ChatResponse> result = Uni.createFrom().item(() -> {
            InferenceRequest internalRequest = chatService.createRequest(
                    request.getModelId(),
                    null,
                    toMessages(request),
                    List.of(),
                    ChatParams.of(request.getTemperature(), request.getMaxTokens()),
                    false);
            
            InferenceResponse response = createCompletion(internalRequest);
            
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
        GrpcResponseObserver.respond(result, observer);
    }

    @Override
    public void streamChat(ChatRequest request, StreamObserver<ChatResponse> observer) {
        LOG.info("Received streamChat request for model: {}", request.getModelId());
        try {
            InferenceRequest internalRequest = chatService.createRequest(
                    request.getModelId(),
                    null,
                    toMessages(request),
                    List.of(),
                    ChatParams.of(request.getTemperature(), request.getMaxTokens()),
                    true);

            sdk.streamCompletion(internalRequest).subscribe().with(
                    chunk -> {
                        meterRegistry.counter("aqli.grpc.chat.stream.chunks").increment();
                        ChatResponse.Builder builder = ChatResponse.newBuilder()
                                .setId(chunk.requestId() != null ? chunk.requestId() : "")
                                .setMessage(tech.kayys.aqli.protobuf.Message.newBuilder()
                                        .setRole("assistant")
                                        .setContent(chunk.delta() != null ? chunk.delta() : "")
                                        .build())
                                .setFinishReason(chunk.finishReason() != null ? chunk.finishReason() : "");
                        if (chunk.usage() != null) {
                            builder.setPromptTokens((int) chunk.usage().inputTokens());
                            builder.setCompletionTokens((int) chunk.usage().outputTokens());
                        }
                        observer.onNext(builder.build());
                    },
                    th -> {
                        LOG.error("Failed streamChat request for model: {}", request.getModelId(), th);
                        meterRegistry.counter("aqli.grpc.chat.stream.error").increment();
                        observer.onError(th);
                    },
                    () -> {
                        LOG.info("streamChat completed for model: {}", request.getModelId());
                        observer.onCompleted();
                    }
            );
        } catch (Exception e) {
            LOG.error("Failed to initiate streamChat for model: {}", request.getModelId(), e);
            observer.onError(e);
        }
    }

    @Override
    public void complete(CompletionRequest request, StreamObserver<CompletionResponse> observer) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received complete request for model: {}", request.getModelId());
        
        Uni<CompletionResponse> result = Uni.createFrom().item(() -> {
            InferenceRequest internalRequest = InferenceRequest.builder()
                    .model(request.getModelId())
                    .prompt(request.getPrompt())
                    .message(new Message(Message.Role.USER, request.getPrompt()))
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .build();
            
            InferenceResponse response = createCompletion(internalRequest);
            
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
        GrpcResponseObserver.respond(result, observer);
    }

    @Override
    public void streamComplete(CompletionRequest request, StreamObserver<CompletionResponse> observer) {
        LOG.info("Received streamComplete request for model: {}", request.getModelId());
        try {
            InferenceRequest internalRequest = InferenceRequest.builder()
                    .model(request.getModelId())
                    .prompt(request.getPrompt())
                    .message(new Message(Message.Role.USER, request.getPrompt()))
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .streaming(true)
                    .build();

            sdk.streamCompletion(internalRequest).subscribe().with(
                    chunk -> {
                        meterRegistry.counter("aqli.grpc.complete.stream.chunks").increment();
                        CompletionResponse.Builder builder = CompletionResponse.newBuilder()
                                .setId(chunk.requestId() != null ? chunk.requestId() : "")
                                .setText(chunk.delta() != null ? chunk.delta() : "")
                                .setFinishReason(chunk.finishReason() != null ? chunk.finishReason() : "");
                        if (chunk.usage() != null) {
                            builder.setPromptTokens((int) chunk.usage().inputTokens());
                            builder.setCompletionTokens((int) chunk.usage().outputTokens());
                        }
                        observer.onNext(builder.build());
                    },
                    th -> {
                        LOG.error("Failed streamComplete request for model: {}", request.getModelId(), th);
                        meterRegistry.counter("aqli.grpc.complete.stream.error").increment();
                        observer.onError(th);
                    },
                    () -> {
                        LOG.info("streamComplete completed for model: {}", request.getModelId());
                        observer.onCompleted();
                    }
            );
        } catch (Exception e) {
            LOG.error("Failed to initiate streamComplete for model: {}", request.getModelId(), e);
            observer.onError(e);
        }
    }

    private static List<Message> toMessages(ChatRequest request) {
        return request.getMessagesList().stream()
                .map(message -> new Message(
                        Message.Role.valueOf(message.getRole().toUpperCase(java.util.Locale.ROOT)),
                        message.getContent()))
                .toList();
    }

    private InferenceResponse createCompletion(InferenceRequest request) {
        try {
            return sdk.createCompletion(request);
        } catch (SdkException exception) {
            throw new CompletionException("AQLI inference request failed", exception);
        }
    }

    private ChatResponse mapChatResponse(InferenceResponse response) {
        return ChatResponse.newBuilder()
                .setId(response.getRequestId())
                .setMessage(tech.kayys.aqli.protobuf.Message.newBuilder()
                        .setRole("assistant")
                        .setContent(response.getContent())
                        .build())
                .setFinishReason(response.getFinishReason() != null ? response.getFinishReason().name() : "STOP")
                .setPromptTokens(response.getInputTokens())
                .setCompletionTokens(response.getOutputTokens())
                .build();
    }

    private CompletionResponse mapCompletionResponse(InferenceResponse response) {
        return CompletionResponse.newBuilder()
                .setId(response.getRequestId())
                .setText(response.getContent())
                .setFinishReason(response.getFinishReason() != null ? response.getFinishReason().name() : "STOP")
                .setPromptTokens(response.getInputTokens())
                .setCompletionTokens(response.getOutputTokens())
                .build();
    }
}
