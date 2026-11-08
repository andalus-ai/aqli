/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;

record ResolvedLayerWeights(
        AccelTensor queryWeight,
        AccelTensor keyWeight,
        AccelTensor valueWeight,
        AccelTensor outputWeight,
        AccelTensor queryBias,
        AccelTensor keyBias,
        AccelTensor valueBias,
        AccelTensor outputBias,
        AccelTensor attentionNormWeight,
        AccelTensor ffnGateWeight,
        AccelTensor ffnGateBias,
        AccelTensor ffnUpWeight,
        AccelTensor ffnUpBias,
        AccelTensor ffnDownWeight,
        AccelTensor ffnDownBias,
        AccelTensor preFfnNormWeight,
        AccelTensor postFfnNormWeight,
        AccelTensor queryNormWeight,
        AccelTensor keyNormWeight,
        AccelTensor postAttnNormWeight,
        AccelTensor perLayerInputGateWeight,
        AccelTensor perLayerProjectionWeight,
        AccelTensor postPerLayerInputNormWeight,
        AccelTensor layerScalarWeight,
        float layerScalarValue,
        AccelTensor mambaInProjWeight,
        AccelTensor mambaConv1dWeight,
        AccelTensor mambaConv1dBias,
        AccelTensor mambaDtBias,
        AccelTensor mambaALogWeight,
        AccelTensor mambaDWeight,
        AccelTensor mambaOutProjWeight,
        AccelTensor mambaNormWeight) {

    ResolvedLayerWeights(
            AccelTensor queryWeight,
            AccelTensor keyWeight,
            AccelTensor valueWeight,
            AccelTensor outputWeight,
            AccelTensor queryBias,
            AccelTensor keyBias,
            AccelTensor valueBias,
            AccelTensor outputBias,
            AccelTensor attentionNormWeight,
            AccelTensor ffnGateWeight,
            AccelTensor ffnGateBias,
            AccelTensor ffnUpWeight,
            AccelTensor ffnUpBias,
            AccelTensor ffnDownWeight,
            AccelTensor ffnDownBias,
            AccelTensor preFfnNormWeight,
            AccelTensor postFfnNormWeight,
            AccelTensor queryNormWeight,
            AccelTensor keyNormWeight,
            AccelTensor postAttnNormWeight,
            AccelTensor perLayerInputGateWeight,
            AccelTensor perLayerProjectionWeight,
            AccelTensor postPerLayerInputNormWeight,
            AccelTensor layerScalarWeight,
            float layerScalarValue) {
        this(queryWeight, keyWeight, valueWeight, outputWeight, queryBias, keyBias, valueBias, outputBias,
                attentionNormWeight, ffnGateWeight, ffnGateBias, ffnUpWeight, ffnUpBias, ffnDownWeight, ffnDownBias,
                preFfnNormWeight, postFfnNormWeight, queryNormWeight, keyNormWeight, postAttnNormWeight,
                perLayerInputGateWeight, perLayerProjectionWeight, postPerLayerInputNormWeight,
                layerScalarWeight, layerScalarValue,
                null, null, null, null, null, null, null, null);
    }

    boolean hasLayerScalar() {
        return layerScalarWeight != null;
    }

    boolean hasMamba() {
        return mambaInProjWeight != null && mambaOutProjWeight != null;
    }
}
