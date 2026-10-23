/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aqli.safetensor.engine.generation.kv.ForwardWorkspace;
import tech.kayys.aljabr.spi.model.FFNActivationType;
import tech.kayys.aljabr.spi.model.ModelArchitecture;
import tech.kayys.aljabr.spi.model.ModelConfig;

record DirectForwardGatedFfnContext(
        DirectForwardLinearContext linearContext,
        boolean decodeLogitsPhase,
        ModelArchitecture arch,
        ForwardWorkspace workspace) {

    DirectForwardRuntimeContext runtime() {
        return linearContext.runtime();
    }

    ModelConfigTraits traits() {
        return linearContext.traits();
    }

    ModelConfig config() {
        return linearContext.config();
    }

    boolean metalLinearEnabled() {
        return runtime().metalLinearEnabled();
    }

    FFNActivationType activationType() {
        return arch.activationType();
    }
}
