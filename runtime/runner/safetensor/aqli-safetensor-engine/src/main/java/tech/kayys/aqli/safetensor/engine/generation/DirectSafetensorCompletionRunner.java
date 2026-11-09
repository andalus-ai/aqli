/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRunProfile;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorTextPolicy;
import tech.kayys.aljabr.safetensor.generation.GenerationConfig;

/**
 * Executes a non-streaming direct SafeTensors completion independently of CLI orchestration.
 *
 * <p>The caller owns request selection, path resolution, and presentation. This runner owns
 * generation configuration, model loading, completion, response sanitization, and adaptation
 * to the AQLI inference response contract.</p>
 */
public final class DirectSafetensorCompletionRunner {

    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(5);

    private DirectSafetensorCompletionRunner() {
    }

    public record Request(
            Path modelPath,
            DirectSafetensorRunProfile profile,
            String prompt,
            String systemPrompt,
            int maxTokens,
            double temperature,
            int topK,
            double topP,
            double repeatPenalty,
            String kvCacheQuantization,
            Long seed,
            String quantizationStrategy) {
    }

    public static tech.kayys.aqli.spi.inference.InferenceResponse run(
            DirectInferenceEngine engine,
            Request request) throws Exception {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(request, "request");
        Path modelPath = Objects.requireNonNull(request.modelPath(), "modelPath");
        DirectSafetensorRunProfile profile = request.profile() == null
                ? DirectSafetensorRunProfile.load(modelPath)
                : request.profile();

        float effectiveRepeatPenalty =
                DirectSafetensorTextPolicy.normalizeRepeatPenalty(profile, request.repeatPenalty());
        GenerationConfig config = GenerationConfig.builder()
                .maxNewTokens(request.maxTokens())
                .strategy(resolveSamplingStrategy(request.temperature(), request.topK(), request.topP()))
                .temperature((float) request.temperature())
                .topK(request.topK())
                .topP((float) request.topP())
                .repetitionPenalty(effectiveRepeatPenalty)
                .kvCacheQuant(resolveKvCacheQuantization(request.kvCacheQuantization()))
                .seed(request.seed() != null ? request.seed().longValue() : -1L)
                .build();

        String preparedPrompt =
                DirectSafetensorTextPolicy.preparePrompt(profile, request.systemPrompt(), request.prompt());
        engine.loadModel(modelPath, null, request.quantizationStrategy());
        tech.kayys.aljabr.spi.inference.InferenceResponse response = engine
                .generate(preparedPrompt, modelPath, config)
                .await()
                .atMost(GENERATION_TIMEOUT);
        var sanitized = DirectSafetensorTextPolicy.sanitizeResponse(response, profile);

        return tech.kayys.aqli.spi.inference.InferenceResponse.builder()
                .requestId(sanitized.getRequestId())
                .content(sanitized.getContent())
                .model(sanitized.getModel())
                .tokensUsed(sanitized.getTokensUsed())
                .inputTokens(sanitized.getInputTokens())
                .outputTokens(sanitized.getOutputTokens())
                .durationMs(sanitized.getDurationMs())
                .metadata(sanitized.getMetadata())
                .build();
    }

    static GenerationConfig.SamplingStrategy resolveSamplingStrategy(
            double temperature,
            int topK,
            double topP) {
        if (temperature < 1.0e-4d || topK == 1) {
            return GenerationConfig.SamplingStrategy.GREEDY;
        }
        boolean hasTopK = topK > 0;
        boolean hasTopP = topP > 0.0d && topP < 1.0d;
        if (hasTopK && hasTopP) {
            return GenerationConfig.SamplingStrategy.TOP_K_TOP_P;
        }
        if (hasTopP) {
            return GenerationConfig.SamplingStrategy.TOP_P;
        }
        if (hasTopK) {
            return GenerationConfig.SamplingStrategy.TOP_K;
        }
        return GenerationConfig.SamplingStrategy.GREEDY;
    }

    static GenerationConfig.KvCacheQuantization resolveKvCacheQuantization(String value) {
        if ("int8".equalsIgnoreCase(value)) {
            return GenerationConfig.KvCacheQuantization.INT8;
        }
        if ("int4".equalsIgnoreCase(value) || "turbo".equalsIgnoreCase(value)) {
            return GenerationConfig.KvCacheQuantization.INT4;
        }
        return GenerationConfig.KvCacheQuantization.NONE;
    }
}
