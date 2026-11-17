package tech.kayys.aqli.server.api.v1;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InferenceRequestHeaderMapperTest {

    @Test
    void replacesDefaultCommunityKeyWithHttpHeader() {
        InferenceRequest request = InferenceRequest.builder()
                .model("model-id")
                .prompt("hello")
                .preferredProvider("safetensor")
                .temperature(0.35)
                .maxTokens(128)
                .build();

        InferenceRequest mapped = InferenceRequestHeaderMapper.withApiKeyHeader(request, "tenant-secret");

        assertEquals("tenant-secret", mapped.getApiKey());
        assertEquals("model-id", mapped.getModel());
        assertEquals("hello", mapped.getPrompt());
        assertEquals("safetensor", mapped.getPreferredProvider().orElseThrow());
        assertEquals(0.35, mapped.getTemperature(), 0.0001);
        assertEquals(128, mapped.getMaxTokens());
    }

    @Test
    void preservesExplicitRequestApiKey() {
        InferenceRequest request = InferenceRequest.builder()
                .apiKey("request-secret")
                .model("model-id")
                .build();

        InferenceRequest mapped = InferenceRequestHeaderMapper.withApiKeyHeader(request, "header-secret");

        assertSame(request, mapped);
        assertEquals("request-secret", mapped.getApiKey());
    }

    @Test
    void leavesRequestUnchangedWhenHeaderIsMissingOrBlank() {
        InferenceRequest request = InferenceRequest.builder().model("model-id").build();

        assertSame(request, InferenceRequestHeaderMapper.withApiKeyHeader(request, null));
        assertSame(request, InferenceRequestHeaderMapper.withApiKeyHeader(request, "  "));
    }
}
