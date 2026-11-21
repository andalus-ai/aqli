package tech.kayys.aqli.spi.embedding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingServiceResultTest {

    @Test
    void legacyResultReceivesSafeUnknownMetadataDefaults() {
        EmbeddingService.EmbeddingResult result = result(new float[] {1.0f, 2.0f});

        assertEquals(2, result.dimension());
        assertNull(result.embeddingSpaceId());
        assertFalse(result.normalized());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void providerCanExposeVectorSpaceProvenance() {
        EmbeddingService.EmbeddingResult result = new EmbeddingService.EmbeddingResult() {
            @Override public float[] vector() { return new float[] {1.0f, 0.0f, 0.0f}; }
            @Override public boolean hasError() { return false; }
            @Override public String errorMessage() { return null; }
            @Override public String embeddingSpaceId() { return "embedding-gemma2:revision-abc:shared-text-image"; }
            @Override public boolean normalized() { return true; }
            @Override public Map<String, String> metadata() {
                return Map.of("model_revision", "revision-abc", "modalities", "text,image");
            }
        };

        assertEquals(3, result.dimension());
        assertEquals("embedding-gemma2:revision-abc:shared-text-image", result.embeddingSpaceId());
        assertTrue(result.normalized());
        assertEquals("text,image", result.metadata().get("modalities"));
    }

    @Test
    void cosineSimilarityReturnsZeroForZeroNormVectors() {
        assertEquals(0.0, result(new float[] {0.0f, 0.0f})
                .cosineSimilarity(result(new float[] {1.0f, 0.0f})));
    }

    private static EmbeddingService.EmbeddingResult result(float[] vector) {
        return new EmbeddingService.EmbeddingResult() {
            @Override public float[] vector() { return vector; }
            @Override public boolean hasError() { return false; }
            @Override public String errorMessage() { return null; }
        };
    }
}
