package tech.kayys.aqli.sdk.local.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.InferenceEngineDescriptor;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.LocalInferenceEngine;

class LocalInferenceEngineCatalogTest {

    @Test
    void selectsOnlyEngineThatSupportsRequest() {
        LocalInferenceEngine gguf = engine("gguf", false);
        LocalInferenceEngine safetensor = engine("safetensor", true);

        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(gguf, safetensor));

        assertSame(safetensor, catalog.select(request(null)).engine());
    }

    @Test
    void explicitProviderPreferenceIsStrict() {
        LocalInferenceEngine gguf = engine("gguf", true);
        LocalInferenceEngine safetensor = engine("safetensor", false);

        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(gguf, safetensor));

        assertSame(gguf, catalog.select(request("gguf")).engine());
        assertThrows(IllegalArgumentException.class, () -> catalog.select(request("safetensor")));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(request("missing")));
    }

    @Test
    void rejectsAmbiguousAutomaticSelection() {
        LocalInferenceEngine first = engine("first", true);
        LocalInferenceEngine second = engine("second", true);
        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(first, second));

        assertThrows(IllegalStateException.class, () -> catalog.select(request(null)));
    }

    @Test
    void ignoresEnginesWithoutDescriptor() {
        LocalInferenceEngine legacy = mock(LocalInferenceEngine.class);
        LocalInferenceEngine described = engine("described", true);
        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(legacy, described));

        assertSame(described, catalog.select(request(null)).engine());
    }

    @Test
    void listsStableProviderIdentifiers() {
        LocalInferenceEngine gguf = engine("gguf", true);
        LocalInferenceEngine safetensor = engine("safetensor", true);
        LocalInferenceEngineCatalog catalog = new LocalInferenceEngineCatalog(List.of(safetensor, gguf));

        assertEquals(List.of("gguf", "safetensor"), catalog.availableProviders());
    }

    private static InferenceRequest request(String preferredProvider) {
        return InferenceRequest.builder()
                .model("model-reference")
                .prompt("hello")
                .preferredProvider(preferredProvider)
                .build();
    }

    private static LocalInferenceEngine engine(String providerId, boolean supports) {
        LocalInferenceEngine engine = mock(LocalInferenceEngine.class,
                withSettings().extraInterfaces(InferenceEngineDescriptor.class));
        InferenceEngineDescriptor descriptor = (InferenceEngineDescriptor) engine;
        when(descriptor.providerId()).thenReturn(providerId);
        when(descriptor.capabilities()).thenReturn(Set.of(InferenceEngineDescriptor.Capability.STREAMING));
        when(descriptor.supports(org.mockito.ArgumentMatchers.any(InferenceRequest.class))).thenReturn(supports);
        return engine;
    }
}
