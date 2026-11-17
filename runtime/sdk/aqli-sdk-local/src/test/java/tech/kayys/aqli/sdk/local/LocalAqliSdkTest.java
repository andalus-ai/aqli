package tech.kayys.aqli.sdk.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.model.repo.hf.HuggingFaceRepository;
import tech.kayys.aljabr.spi.download.DownloadProgressListener;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aqli.spi.inference.InferenceEngineDescriptor;
import tech.kayys.aljabr.spi.model.ModelInfo;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aqli.spi.inference.LocalInferenceEngine;

class LocalAqliSdkTest {

    @Test
    void executesInferenceThroughSelectedEngine() throws Exception {
        LocalInferenceEngine engine = engine();
        InferenceResponse expected = InferenceResponse.builder()
                .requestId("request-1")
                .model("model")
                .content("hello")
                .build();
        when(engine.infer(org.mockito.ArgumentMatchers.any(InferenceRequest.class)))
                .thenReturn(Uni.createFrom().item(expected));

        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(engine));
        LocalAqliSdk sdk = new LocalAqliSdk(catalog, SdkConfig.builder()
                .requestTimeout(Duration.ofSeconds(2)).build());

        InferenceResponse actual = sdk.createCompletion(InferenceRequest.builder()
                .model("model")
                .prompt("hi")
                .build());

        assertEquals(expected, actual);
    }


    @Test
    void preservesGeneratedToolCallsInTypedTextResponse() {
        LocalInferenceEngine engine = engine();
        when(engine.infer(any(InferenceRequest.class))).thenReturn(Uni.createFrom().item(
                InferenceResponse.builder()
                        .requestId("tool-request")
                        .model("model")
                        .content("")
                        .finishReason(InferenceResponse.FinishReason.TOOL_CALLS)
                        .toolCall(new InferenceResponse.ToolCall("lookup", Map.of("query", "weather")))
                        .build()));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)), SdkConfig.builder().build());

        TextGenerationResponse response = sdk.generateText(TextGenerationRequest.of("model", "lookup weather"));

        assertEquals(TextGenerationResponse.FinishReason.TOOL_CALLS, response.finishReason());
        assertEquals(1, response.toolCalls().size());
        assertEquals("lookup", response.toolCalls().getFirst().getFunction().getName());
        assertEquals(Map.of("query", "weather"), response.toolCalls().getFirst().getArguments());
        org.junit.jupiter.api.Assertions.assertFalse(response.toolCalls().getFirst().getId().isBlank());
    }

    @Test
    void wrapsCheckedInferenceFailuresAtTheTypedSynchronousBoundary() {
        LocalInferenceEngine engine = engine();
        when(engine.infer(any(InferenceRequest.class)))
                .thenReturn(Uni.createFrom().failure(new IllegalArgumentException("boom")));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)), SdkConfig.builder().build());

        IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> sdk.generateText(TextGenerationRequest.of("model", "hello")));

        org.junit.jupiter.api.Assertions.assertInstanceOf(
                tech.kayys.aqli.sdk.exception.SdkException.class, failure.getCause());
    }

    @Test
    void executesTypedTextCapabilityThroughTheSameLocalInferenceEngine() {
        LocalInferenceEngine engine = engine();
        InferenceResponse expected = InferenceResponse.builder()
                .requestId("typed-request")
                .model("model")
                .content("typed hello")
                .inputTokens(2)
                .outputTokens(2)
                .durationMs(12)
                .build();
        when(engine.infer(any(InferenceRequest.class)))
                .thenReturn(Uni.createFrom().item(expected));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)),
                SdkConfig.builder().requestTimeout(Duration.ofSeconds(2)).build());

        var actual = sdk.generateText(TextGenerationRequest.of("model", "hello"));

        assertEquals("typed hello", actual.content());
        assertEquals(2, actual.inputTokens());
        assertEquals(2, actual.outputTokens());
        assertEquals(12, actual.durationMs());

        org.mockito.ArgumentCaptor<InferenceRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InferenceRequest.class);
        verify(engine).infer(captor.capture());
        assertEquals("model", captor.getValue().getModel());
        assertEquals("hello", captor.getValue().getPrompt());
    }




    @Test
    void exposesCanonicalPublicSdkTextCapabilityContract() {
        LocalInferenceEngine engine = engine();
        when(engine.infer(any(InferenceRequest.class))).thenReturn(Uni.createFrom().item(
                InferenceResponse.builder()
                        .requestId("public-sdk")
                        .model("model")
                        .content("public contract")
                        .inputTokens(1)
                        .outputTokens(2)
                        .build()));

        tech.kayys.aqli.sdk.AqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)), SdkConfig.builder().build());

        TextGenerationResponse response = sdk.generateText(TextGenerationRequest.of("model", "hello"));

        assertEquals("public contract", response.content());
        assertEquals(3, response.totalTokens());
        org.mockito.ArgumentCaptor<InferenceRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InferenceRequest.class);
        verify(engine).infer(captor.capture());
        assertEquals("model", captor.getValue().getModel());
        assertEquals("hello", captor.getValue().getPrompt());
    }

    @Test
    void executesTypedTextCapabilityAsynchronously() throws Exception {
        LocalInferenceEngine engine = engine();
        when(engine.infer(any(InferenceRequest.class))).thenReturn(Uni.createFrom().item(
                InferenceResponse.builder()
                        .requestId("async-typed")
                        .model("model")
                        .content("async hello")
                        .inputTokens(1)
                        .outputTokens(2)
                        .build()));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)), SdkConfig.builder().build());

        TextGenerationResponse response = sdk.generateTextAsync(TextGenerationRequest.of("model", "hello"))
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertEquals("async hello", response.content());
        assertEquals(3, response.totalTokens());
    }

    @Test
    void adaptsLocalInferenceStreamToTypedTextChunks() throws Exception {
        LocalInferenceEngine engine = engine();
        when(engine.stream(any(InferenceRequest.class))).thenReturn(Multi.createFrom().items(
                StreamingInferenceChunk.textDelta("stream-1", 0, "hello"),
                StreamingInferenceChunk.finalTextChunk("stream-1", 1, " world", null)));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of(engine)), SdkConfig.builder().build());
        List<TextGenerationChunk> chunks = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);

        sdk.streamText(TextGenerationRequest.of("model", "hello")).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(TextGenerationChunk chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.countDown();
                throw new AssertionError("Unexpected stream failure", throwable);
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });

        org.junit.jupiter.api.Assertions.assertTrue(completed.await(2, TimeUnit.SECONDS));
        assertEquals(2, chunks.size());
        assertEquals("hello", chunks.get(0).delta());
        assertEquals(" world", chunks.get(1).delta());
        assertEquals(true, chunks.get(1).terminal());
        assertEquals(TextGenerationResponse.FinishReason.STOP, chunks.get(1).finishReason());
        assertEquals(TextGenerationChunk.class, chunks.get(0).getClass());
    }

    @Test
    void streamsInferenceThroughTheSelectedEngine() {
        LocalInferenceEngine engine = engine();
        when(engine.stream(any(InferenceRequest.class)))
                .thenReturn(Multi.createFrom().empty());
        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(engine));
        LocalAqliSdk sdk = new LocalAqliSdk(catalog, SdkConfig.builder().build());

        sdk.streamCompletion(InferenceRequest.builder()
                .model("model")
                .prompt("hi")
                .build())
                .collect().asList().await().indefinitely();

        org.mockito.ArgumentCaptor<InferenceRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InferenceRequest.class);
        verify(engine).stream(captor.capture());
        assertEquals(true, captor.getValue().isStreaming());
    }

    @Test
    void appliesConfiguredProviderOnlyWhenRequestDoesNotSpecifyOne() {
        LocalInferenceEngineCatalog catalog = mock(LocalInferenceEngineCatalog.class);
        LocalInferenceEngine engine = engine();
        when(catalog.select(org.mockito.ArgumentMatchers.any(InferenceRequest.class)))
                .thenReturn(new LocalInferenceEngineCatalog.Selection("configured", engine,
                        (InferenceEngineDescriptor) engine));
        LocalAqliSdk sdk = new LocalAqliSdk(catalog,
                SdkConfig.builder().preferredProvider("configured").build());

        InferenceRequest request = InferenceRequest.builder().model("model").prompt("hi").build();
        sdk.createCompletionAsync(request);

        org.mockito.ArgumentCaptor<InferenceRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InferenceRequest.class);
        org.mockito.Mockito.verify(catalog).select(captor.capture());
        assertEquals("configured", captor.getValue().getPreferredProvider().orElseThrow());
    }

    @Test
    void exposesTheComposedLocalModelCatalogue() throws Exception {
        LocalModelCatalog models = mock(LocalModelCatalog.class);
        ModelInfo model = ModelInfo.builder().modelId("org/model").name("Model").build();
        when(models.listModels(0, Integer.MAX_VALUE)).thenReturn(List.of(model));
        when(models.listModels(2, 5)).thenReturn(List.of(model));
        when(models.findModel("org/model")).thenReturn(Optional.of(model));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of()), null, models);

        assertEquals(List.of(model), sdk.listModels());
        assertEquals(List.of(model), sdk.listModels(2, 5));
        assertEquals(Optional.of(model), sdk.getModelInfo("org/model"));
    }

    @Test
    void pullsHuggingFaceModelsThroughRepositoryAndForwardsProgress() throws Exception {
        HuggingFaceRepository repository = mock(HuggingFaceRepository.class);
        when(repository.pullRepository(
                eq("org/model"), eq("v2"), eq("onnx"), isNull(),
                eq(Map.of()), eq(List.of()), eq(true), isNull(),
                any(DownloadProgressListener.class)))
                .thenReturn(Path.of("/tmp/aqli-model"));

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of()),
                null,
                mock(LocalModelCatalog.class),
                repository);
        List<PullProgress> progress = new ArrayList<>();

        sdk.pullModel("hf:org/model", "v2", "onnx", true, progress::add);

        org.mockito.ArgumentCaptor<DownloadProgressListener> listener =
                org.mockito.ArgumentCaptor.forClass(DownloadProgressListener.class);
        verify(repository).pullRepository(
                eq("org/model"), eq("v2"), eq("onnx"), isNull(),
                eq(Map.of()), eq(List.of()), eq(true), isNull(), listener.capture());

        listener.getValue().onProgress(512, 1024, 0.5);
        listener.getValue().onComplete(1024);

        assertEquals(2, progress.size());
        assertEquals(1024, progress.get(0).getTotal());
        assertEquals(512, progress.get(0).getCompleted());
        assertEquals(100, progress.get(1).getPercentComplete());
        assertEquals("Completed org/model", progress.get(1).getStatus());
    }

    @Test
    void pullsHuggingFaceModelsWithTypedRepositoryOptions() throws Exception {
        HuggingFaceRepository repository = mock(HuggingFaceRepository.class);
        Path target = Path.of("/tmp/aqli-filtered-model");
        when(repository.pullRepository(
                eq("org/model"), eq("release"), eq("gguf"), eq("moss-audio-tokenizer"),
                eq(Map.of("fallback", "typed-capability-test")), eq(List.of("*.gguf")), eq(true), eq("text"),
                any(DownloadProgressListener.class)))
                .thenReturn(target);

        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of()),
                null,
                mock(LocalModelCatalog.class),
                repository);
        List<PullProgress> progress = new ArrayList<>();

        var result = sdk.pullHuggingFaceModel(HuggingFacePullRequest.builder()
                .modelSpec("hf:org/model")
                .revision("release")
                .format("gguf")
                .includePatterns(List.of("*.gguf"))
                .force(true)
                .taskType("text")
                .architecture("moss-audio-tokenizer")
                .metadata(Map.of("fallback", "typed-capability-test"))
                .build(), progress::add);

        assertEquals("org/model", result.repositoryId());
        assertEquals(target.toUri(), result.location());
        org.mockito.ArgumentCaptor<DownloadProgressListener> listener =
                org.mockito.ArgumentCaptor.forClass(DownloadProgressListener.class);
        verify(repository).pullRepository(
                eq("org/model"), eq("release"), eq("gguf"), eq("moss-audio-tokenizer"),
                eq(Map.of("fallback", "typed-capability-test")), eq(List.of("*.gguf")), eq(true), eq("text"), listener.capture());

        listener.getValue().onProgress(512, 1024, 0.5);
        listener.getValue().onComplete(1024);
        assertEquals(2, progress.size());
        assertEquals(512, progress.get(0).getCompleted());
        assertEquals(100, progress.get(1).getPercentComplete());
    }

    @Test
    void rejectsNonHuggingFacePullsUntilTheirRepositoryCapabilitiesAreComposed() {
        LocalAqliSdk sdk = new LocalAqliSdk(
                new LocalInferenceEngineCatalog(List.of()),
                null,
                mock(LocalModelCatalog.class),
                mock(HuggingFaceRepository.class));

        org.junit.jupiter.api.Assertions.assertThrows(
                tech.kayys.aqli.sdk.exception.SdkException.class,
                () -> sdk.pullModel("ollama:llama3", null));
    }

    private static LocalInferenceEngine engine() {
        LocalInferenceEngine engine = mock(LocalInferenceEngine.class,
                org.mockito.Mockito.withSettings().extraInterfaces(InferenceEngineDescriptor.class));
        InferenceEngineDescriptor descriptor = (InferenceEngineDescriptor) engine;
        when(descriptor.providerId()).thenReturn("unit-test");
        when(descriptor.capabilities()).thenReturn(Set.of(InferenceEngineDescriptor.Capability.STREAMING));
        when(descriptor.supports(org.mockito.ArgumentMatchers.any(InferenceRequest.class))).thenReturn(true);
        return engine;
    }
}
