/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aqli.safetensor.engine.generation.attention.FlashAttentionKernel;
import tech.kayys.aqli.safetensor.engine.generation.moe.MoeForwardPass;

record DirectForwardSequenceContext(
        DirectForwardRuntimeContext runtime,
        FlashAttentionKernel attentionKernel,
        MoeForwardPass moeForwardPass,
        ModelConfigTraits traits,
        DirectForwardOperators operators) {
}
