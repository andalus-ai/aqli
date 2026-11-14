package tech.kayys.aqli.sdk.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.model.repo.hf.HuggingFaceRepository;
import tech.kayys.aljabr.spi.download.DownloadProgressListener;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aqli.spi.inference.InferenceEngineDescriptor;
import tech.kayys.aljabr.spi.model.ModelInfo;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
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
