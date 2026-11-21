package tech.kayys.aqli.spi.embedding;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.spi.model.MultimodalContent;
import java.util.List;
import java.util.Map;

/**
 * Service for generating embeddings from multimodal content.
 *
 * <p>Implementations should expose a stable embedding-space identifier whenever
 * the backing model defines one. Consumers must not assume that vectors with
 * equal dimensions are comparable: model revision, projection, preprocessing,
 * and modality can all change the vector space.</p>
 */
public interface EmbeddingService {

    /** Generate embedding for a single content item. */
    Uni<EmbeddingResult> embed(MultimodalContent content, String model);

    /** Generate embeddings for a batch of content items. */
    Uni<List<EmbeddingResult>> embedBatch(List<MultimodalContent> contents, String model);

    /**
     * Result of an embedding operation.
     *
     * <p>The default metadata methods preserve source compatibility for existing
     * providers. Providers should override {@link #embeddingSpaceId()} and
     * {@link #metadata()} when they can identify the exact vector space.</p>
     */
    interface EmbeddingResult {
        float[] vector();
        boolean hasError();
        String errorMessage();

        /** Vector width, or zero when the provider returned no vector. */
        default int dimension() {
            float[] value = vector();
            return value == null ? 0 : value.length;
        }

        /**
         * Stable identifier for the vector space, or {@code null} when unknown.
         * Equal dimensions alone do not imply compatible embedding spaces.
         */
        default String embeddingSpaceId() {
            return null;
        }

        /** Whether the returned vector has been L2-normalized. */
        default boolean normalized() {
            return false;
        }

        /**
         * Provider-defined provenance such as model revision, modality,
         * preprocessing version, and output dimension. Empty when unavailable.
         */
        default Map<String, String> metadata() {
            return Map.of();
        }

        default double cosineSimilarity(EmbeddingResult other) {
            float[] a = this.vector();
            float[] b = other.vector();
            if (a == null || b == null || a.length != b.length) return 0.0;
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) {
                dot += (double) a[i] * b[i];
                na  += (double) a[i] * a[i];
                nb  += (double) b[i] * b[i];
            }
            if (na == 0.0 || nb == 0.0) return 0.0;
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        }
    }
}
