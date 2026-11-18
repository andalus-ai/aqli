package tech.kayys.aqli.spi.embedding;

/**
 * Semantic intent for embedding requests. Kept name-compatible with Aljabr's
 * canonical {@code EmbeddingTask} for lossless compatibility conversion.
 */
public enum EmbeddingTask {
    UNSPECIFIED,
    RETRIEVAL_QUERY,
    RETRIEVAL_DOCUMENT,
    SEMANTIC_SIMILARITY,
    CLASSIFICATION,
    CLUSTERING
}
