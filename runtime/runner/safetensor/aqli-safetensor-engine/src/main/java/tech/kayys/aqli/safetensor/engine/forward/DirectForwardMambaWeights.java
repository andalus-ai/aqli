/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;

/**
 * Cached float arrays and tensors for a layer's Mamba SSM module.
 */
final class DirectForwardMambaWeights {
    final AccelTensor inProjWeight;
    final AccelTensor outProjWeight;
    final float[] conv1dWeight;
    final float[] conv1dBias;
    final float[] dtBias;
    final float[] aLog;
    final float[] aVal;
    final float[] dWeight;

    DirectForwardMambaWeights(
            AccelTensor inProjWeight,
            AccelTensor outProjWeight,
            float[] conv1dWeight,
            float[] conv1dBias,
            float[] dtBias,
            float[] aLog,
            float[] dWeight) {
        this.inProjWeight = inProjWeight;
        this.outProjWeight = outProjWeight;
        this.conv1dWeight = conv1dWeight;
        this.conv1dBias = conv1dBias;
        this.dtBias = dtBias;
        this.aLog = aLog;
        if (aLog != null) {
            this.aVal = new float[aLog.length];
            for (int h = 0; h < aLog.length; h++) {
                this.aVal[h] = (float) -Math.exp(aLog[h]);
            }
        } else {
            this.aVal = null;
        }
        this.dWeight = dWeight;
    }

    private static float[] toFloats(AccelTensor tensor) {
        if (tensor == null) {
            return null;
        }
        AccelTensor f32 = tensor.dequantize();
        try {
            return f32.toFloatArray();
        } finally {
            if (f32 != tensor) {
                f32.close();
            }
        }
    }

    static DirectForwardMambaWeights fromLayer(ResolvedLayerWeights layer) {
        if (!layer.hasMamba()) {
            return null;
        }
        float[] conv1dW = toFloats(layer.mambaConv1dWeight());
        float[] conv1dB = toFloats(layer.mambaConv1dBias());
        float[] dtB = toFloats(layer.mambaDtBias());
        float[] aLogArr = toFloats(layer.mambaALogWeight());
        float[] dArr = toFloats(layer.mambaDWeight());

        return new DirectForwardMambaWeights(
                layer.mambaInProjWeight(),
                layer.mambaOutProjWeight(),
                conv1dW,
                conv1dB,
                dtB,
                aLogArr,
                dArr);
    }
}
