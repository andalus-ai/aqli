/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aqli.safetensor.engine.generation.kv.ForwardWorkspace;
import tech.kayys.aljabr.spi.model.ModelConfig;

record DirectForwardOutputProjectionContext(
        DirectForwardRuntimeContext runtime,
        ModelConfigTraits traits,
        ModelConfig config,
        ResolvedModelWeights resolvedWeights,
        ForwardWorkspace workspace) {
}
