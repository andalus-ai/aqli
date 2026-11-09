package tech.kayys.aqli.sdk.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aqli.spi.inference.InferenceEngineDescriptor;
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
    void reportsUnsupportedOperationsInsteadOfReturningPlaceholderResults() {
        LocalAqliSdk sdk = new LocalAqliSdk(new LocalInferenceEngineCatalog(List.of()), null);

        SdkException error = assertThrows(SdkException.class,
                () -> sdk.listModels());

        assertEquals("SDK_ERR_NOT_SUPPORTED", error.getErrorCode());
        assertTrue(error.getMessage().contains("no production runtime service"));
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
