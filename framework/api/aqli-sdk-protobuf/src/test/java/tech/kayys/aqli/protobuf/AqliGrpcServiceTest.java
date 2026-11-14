package tech.kayys.aqli.protobuf;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AqliGrpcServiceTest {

    @Mock
    private AqliRuntime sdk;

    private AqliGrpcService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws Exception {
        service = new AqliGrpcService();
        meterRegistry = new SimpleMeterRegistry();

        Field sdkField = AqliGrpcService.class.getDeclaredField("sdk");
        sdkField.setAccessible(true);
        sdkField.set(service, sdk);

        Field meterField = AqliGrpcService.class.getDeclaredField("meterRegistry");
        meterField.setAccessible(true);
        meterField.set(service, meterRegistry);
    }

    @Test
    void testChatSynchronous() throws Exception {
        InferenceResponse mockResponse = InferenceResponse.builder()
                .requestId("req-123")
                .content("Hello from model")
                .finishReason(InferenceResponse.FinishReason.STOP)
                .inputTokens(12)
                .outputTokens(8)
                .build();

        when(sdk.createCompletion(any(InferenceRequest.class))).thenReturn(mockResponse);

        ChatRequest request = ChatRequest.newBuilder()
                .setModelId("test-model")
                .addMessages(Message.newBuilder().setRole("USER").setContent("Hi").build())
                .setTemperature(0.7f)
                .setMaxTokens(50)
                .build();

        TestStreamObserver<ChatResponse> observer = new TestStreamObserver<>();
        service.chat(request, observer);

        ArgumentCaptor<InferenceRequest> requestCaptor = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(sdk).createCompletion(requestCaptor.capture());
        InferenceRequest assembled = requestCaptor.getValue();
        assertEquals("test-model", assembled.getModel());
        assertEquals(0.7, assembled.getTemperature(), 0.0001);
        assertEquals(50, assembled.getMaxTokens());
        assertEquals(0.9, assembled.getTopP(), 0.0001);
        assertEquals(1.1, assembled.getRepeatPenalty(), 0.0001);
        assertEquals(1, assembled.getMessages().size());
        assertEquals(Message.Role.USER, assembled.getMessages().get(0).getRole());
        assertFalse(assembled.isStreaming());

        assertTrue(observer.completed);
        assertEquals(1, observer.responses.size());
        ChatResponse response = observer.responses.get(0);
        assertEquals("req-123", response.getId());
        assertEquals("assistant", response.getMessage().getRole());
        assertEquals("Hello from model", response.getMessage().getContent());
        assertEquals("STOP", response.getFinishReason());
        assertEquals(12, response.getPromptTokens());
        assertEquals(8, response.getCompletionTokens());
    }

    @Test
    void testCompleteSynchronous() throws Exception {
        InferenceResponse mockResponse = InferenceResponse.builder()
                .requestId("req-456")
                .content("Completion output")
                .finishReason(InferenceResponse.FinishReason.LENGTH)
                .inputTokens(5)
                .outputTokens(20)
                .build();

        when(sdk.createCompletion(any(InferenceRequest.class))).thenReturn(mockResponse);

        CompletionRequest request = CompletionRequest.newBuilder()
                .setModelId("test-model")
                .setPrompt("Once upon a time")
                .setTemperature(0.5f)
                .setMaxTokens(20)
                .build();

        TestStreamObserver<CompletionResponse> observer = new TestStreamObserver<>();
        service.complete(request, observer);

        assertTrue(observer.completed);
        assertEquals(1, observer.responses.size());
        CompletionResponse response = observer.responses.get(0);
        assertEquals("req-456", response.getId());
        assertEquals("Completion output", response.getText());
        assertEquals("LENGTH", response.getFinishReason());
        assertEquals(5, response.getPromptTokens());
        assertEquals(20, response.getCompletionTokens());
    }

    @Test
    void testStreamChat() {
        StreamingInferenceChunk chunk1 = StreamingInferenceChunk.of("req-stream-1", 0, "Hello ");
        StreamingInferenceChunk chunk2 = StreamingInferenceChunk.finalTextChunk(
                "req-stream-1", 1, "world!", new StreamingInferenceChunk.ChunkUsage(10, 5, 120));

        when(sdk.streamCompletion(any(InferenceRequest.class))).thenReturn(Multi.createFrom().items(chunk1, chunk2));

        ChatRequest request = ChatRequest.newBuilder()
                .setModelId("test-model")
                .addMessages(Message.newBuilder().setRole("USER").setContent("Hello").build())
                .build();

        TestStreamObserver<ChatResponse> observer = new TestStreamObserver<>();
        service.streamChat(request, observer);

        ArgumentCaptor<InferenceRequest> requestCaptor = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(sdk).streamCompletion(requestCaptor.capture());
        InferenceRequest assembled = requestCaptor.getValue();
        assertEquals("test-model", assembled.getModel());
        assertEquals(0.9, assembled.getTopP(), 0.0001);
        assertEquals(1.1, assembled.getRepeatPenalty(), 0.0001);
        assertTrue(assembled.isStreaming());

        assertTrue(observer.completed);
        assertEquals(2, observer.responses.size());
        assertEquals("Hello ", observer.responses.get(0).getMessage().getContent());
        assertEquals("world!", observer.responses.get(1).getMessage().getContent());
        assertEquals("stop", observer.responses.get(1).getFinishReason());
        assertEquals(10, observer.responses.get(1).getPromptTokens());
        assertEquals(5, observer.responses.get(1).getCompletionTokens());
    }

    @Test
    void testStreamComplete() {
        StreamingInferenceChunk chunk1 = StreamingInferenceChunk.of("req-stream-2", 0, "Dart ");
        StreamingInferenceChunk chunk2 = StreamingInferenceChunk.finalTextChunk(
                "req-stream-2", 1, "code", new StreamingInferenceChunk.ChunkUsage(8, 4, 90));

        when(sdk.streamCompletion(any(InferenceRequest.class))).thenReturn(Multi.createFrom().items(chunk1, chunk2));

        CompletionRequest request = CompletionRequest.newBuilder()
                .setModelId("test-model")
                .setPrompt("Dart")
                .build();

        TestStreamObserver<CompletionResponse> observer = new TestStreamObserver<>();
        service.streamComplete(request, observer);

        assertTrue(observer.completed);
        assertEquals(2, observer.responses.size());
        assertEquals("Dart ", observer.responses.get(0).getText());
        assertEquals("code", observer.responses.get(1).getText());
        assertEquals("stop", observer.responses.get(1).getFinishReason());
        assertEquals(8, observer.responses.get(1).getPromptTokens());
        assertEquals(4, observer.responses.get(1).getCompletionTokens());
    }

    private static class TestStreamObserver<T> implements StreamObserver<T> {
        final List<T> responses = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
