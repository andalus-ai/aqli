/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;

record DirectForwardPerLayerResidualWeights(
        AccelTensor gateWeight,
        AccelTensor projectionWeight,
        AccelTensor normWeight) {

    static DirectForwardPerLayerResidualWeights fromLayer(ResolvedLayerWeights layerWeights) {
        return new DirectForwardPerLayerResidualWeights(
                layerWeights.perLayerInputGateWeight(),
                layerWeights.perLayerProjectionWeight(),
                layerWeights.postPerLayerInputNormWeight());
    }

    boolean complete() {
        return gateWeight != null && projectionWeight != null && normWeight != null;
    }
}
