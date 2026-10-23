/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aljabr.spi.model.ModelArchitecture;
import tech.kayys.aljabr.spi.model.ModelConfig;

import java.util.Map;

record ResolvedWeightsCacheEntry(
        Map<String, AccelTensor> weights,
        ModelConfig config,
        ModelArchitecture arch,
        ResolvedModelWeights resolved) {
}
