/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * @author Bhangun
 */

package tech.kayys.aqli.spi.inference;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import tech.kayys.aljabr.spi.model.HealthStatus;

/**
 * Main entry point for inference requests.
 * Thread-safe and stateless.
 */
public interface InferenceEngine {

        /**
         * Execute synchronous inference
         */
        Uni<InferenceResponse> infer(
                        InferenceRequest request);

        /**
         * Execute inference asynchronously
         */
        Uni<InferenceResponse> executeAsync(
                        String modelId,
                        InferenceRequest request);

        /**
         * Execute inference synchronously (blocking)
         */
        InferenceResponse execute(
                        String modelId,
                        InferenceRequest request);

        /**
         * Execute streaming inference
         */
        Multi<StreamingInferenceChunk> stream(InferenceRequest request);

        /**
         * Execute streaming inference with model selection
         */
        Multi<StreamingInferenceChunk> streamExecute(
                        String modelId,
                        InferenceRequest request);

        /**
         * Execute embedding generation
         */
        Uni<tech.kayys.aqli.spi.embedding.EmbeddingResponse> executeEmbedding(String modelId,
                        tech.kayys.aqli.spi.embedding.EmbeddingRequest request);

        /**
         * Submit asynchronous inference job
         */
        Uni<String> submitAsyncJob(InferenceRequest request);

        /**
         * Shutdown the inference engine gracefully
         */
        void shutdown();

        /**
         * Get engine health status
         */
        boolean isHealthy();

        /**
         * Health check
         */
        HealthStatus health();

        /**
         * Initialize the inference engine
         */
        void initialize();

        /**
         * Get engine statistics
         */
        EngineStats getStats();

        /** Canonical Aljabr embedding execution entry point. */
        default Uni<tech.kayys.aljabr.spi.embedding.EmbeddingResponse> executeEmbedding(
                        String modelId, tech.kayys.aljabr.spi.embedding.EmbeddingRequest request) {
                return executeEmbedding(modelId,
                                tech.kayys.aqli.spi.inference.compat.AljabrInferenceDtoAdapter.fromAljabr(request))
                                .map(tech.kayys.aqli.spi.inference.compat.AljabrInferenceDtoAdapter::toAljabr);
        }

        /**
         * Engine statistics data
         */
        record EngineStats(
                        long activeInferences,
                        long totalInferences,
                        long failedInferences,
                        double avgLatencyMs,
                        String status) {
        }
}
