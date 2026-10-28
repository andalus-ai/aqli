/*
 * AQLI Inference Runtime
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation;

import java.nio.file.Path;
import java.util.Map;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;

/**
 * AQLI-owned optional quantization port for inference-time weight preparation.
 *
 * <p>The inference engine depends only on this contract. An optional integration module may
 * adapt Aljabr/Tafkir quantization implementations to it; standard unquantized inference does
 * not require those modules on the compile or runtime classpath.</p>
 */
public interface InferenceQuantizer {

    enum QuantStrategy {
        NONE,
        INT4,
        INT8,
        FP8,
        TURBO,
        AWQ,
        GPTQ,
        BNB
    }

    record Result(
            Map<String, AccelTensor> weights,
            String cacheState,
            Path cachePath,
            int cacheHits,
            int quantizedFresh) {
    }

    Result quantizeWeightsForInferenceDetailed(
            Path modelPath,
            Map<String, AccelTensor> weights,
            QuantStrategy strategy);
}
