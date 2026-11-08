/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import tech.kayys.aljabr.safetensor.core.tensor.AccelOps;
import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aljabr.spi.model.ModelConfig;
import tech.kayys.aqli.safetensor.engine.generation.kv.KVCacheManager;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Mamba-2 State Space Model (SSM) forward stage for hybrid architectures
 * (Falcon-H1).
 */
final class DirectForwardMambaStage {

    private DirectForwardMambaStage() {
    }

    private static float silu(float x) {
        return (float) (x / (1.0 + Math.exp(-x)));
    }

    private static float softplus(float x) {
        if (x > 20.0f) {
            return x;
        }
        if (x < -20.0f) {
            return (float) Math.exp(x);
        }
        return (float) Math.log1p(Math.exp(x));
    }

    static AccelTensor compute(DirectForwardLayerStageContext ctx,
                              DirectForwardMambaWeights weights,
                              KVCacheManager.KVCacheSession kvCache,
                              MemorySegment normedInput) {
        ModelConfig config = ctx.config();
        int seqLen = ctx.seqLen();
        int hiddenSize = config.hiddenSize();
        int numLayers = config.numHiddenLayers();

        int intermediateSize = config.resolvedMambaDSsm();
        int nGroups = config.resolvedMambaNGroups();
        int ssmStateSize = config.resolvedMambaDState();
        int dConv = config.resolvedMambaDConv();
        int numHeads = config.resolvedMambaNHeads();
        int headDim = config.resolvedMambaDHead();
        int convDim = intermediateSize + 2 * nGroups * ssmStateSize;
        int groupTimeStateSize = nGroups * ssmStateSize;
        int projSize = intermediateSize + convDim + numHeads;

        // Retrieve or initialize Mamba state cache
        DirectForwardMambaState mambaState = (DirectForwardMambaState) kvCache.getMambaState();
        if (mambaState == null) {
            mambaState = new DirectForwardMambaState(numLayers, convDim, dConv, numHeads, headDim, ssmStateSize);
            kvCache.setMambaState(mambaState);
        }

        float[] convBuf = mambaState.convState(ctx.layerIdx());
        float[] ssm = mambaState.ssmState(ctx.layerIdx());

        float[] convW = weights.conv1dWeight;
        float[] convB = weights.conv1dBias;
        float[] dtBiasArr = weights.dtBias;
        float[] aLogArr = weights.aLog;
        float[] dArr = weights.dWeight;

        float timeStepMin = (float) (config.timeStepMin() != null ? config.timeStepMin() : 0.001);
        float timeStepMax = (float) (config.timeStepMax() != null ? config.timeStepMax() : 0.1);

        double[] ssmMultipliers = config.resolvedSsmMultipliers();
        float ssmInMult = config.resolvedSsmInMultiplier();
        float ssmOutMult = config.resolvedSsmOutMultiplier();

        float multGate = (float) (ssmInMult * ssmMultipliers[0]);
        float multX = (float) (ssmInMult * ssmMultipliers[1]);
        float multB = (float) (ssmInMult * ssmMultipliers[2]);
        float multC = (float) (ssmInMult * ssmMultipliers[3]);
        float multDt = (float) (ssmInMult * ssmMultipliers[4]);

        // 1. in_proj: [1, seqLen, hiddenSize] @ inProjWeight^T -> [1, seqLen, projSize]
        AccelTensor normedTensor = AccelTensor.view(normedInput, new long[] { 1L, seqLen, hiddenSize });
        AccelTensor inProjTensor;
        try {
            DirectForwardLinearContext linCtx = new DirectForwardLinearContext(
                    ctx.runtime(), ctx.traits(), config);
            DirectForwardLinearRequest linReq = DirectForwardLinearRequest.projection(
                    linCtx, normedTensor, weights.inProjWeight, null, "mamba_in_proj", null);
            inProjTensor = DirectForwardLinearProjection.linear(linReq);
        } finally {
            normedTensor.close();
        }

        float[] inProjData = inProjTensor.toFloatArray();
        inProjTensor.close();



        // 2. Token-by-token recurrence across seqLen
        float[] scanOutputAll = new float[seqLen * intermediateSize];
        float[] convOut = new float[convDim];
        float[] x = new float[intermediateSize];
        float[] B = new float[groupTimeStateSize];
        float[] C = new float[groupTimeStateSize];
        float[] dt = new float[numHeads];
        float[] gate = new float[intermediateSize];

        int headsPerGroup = numHeads / nGroups;

        for (int t = 0; t < seqLen; t++) {
            int tokenProjOffset = t * projSize;

            // Extract gate with multiplier
            if (multGate != 1.0f) {
                for (int i = 0; i < intermediateSize; i++) {
                    gate[i] = inProjData[tokenProjOffset + i] * multGate;
                }
            } else {
                System.arraycopy(inProjData, tokenProjOffset, gate, 0, intermediateSize);
            }

            // Extract conv inputs with multipliers: [x_part, B_part, C_part]
            int convInputOffset = tokenProjOffset + intermediateSize;
            if (multX != 1.0f) {
                for (int i = 0; i < intermediateSize; i++) {
                    inProjData[convInputOffset + i] *= multX;
                }
            }
            if (multB != 1.0f) {
                for (int i = 0; i < groupTimeStateSize; i++) {
                    inProjData[convInputOffset + intermediateSize + i] *= multB;
                }
            }
            if (multC != 1.0f) {
                for (int i = 0; i < groupTimeStateSize; i++) {
                    inProjData[convInputOffset + intermediateSize + groupTimeStateSize + i] *= multC;
                }
            }

            // Extract dt with multiplier
            int dtOffset = tokenProjOffset + intermediateSize + convDim;
            if (multDt != 1.0f) {
                for (int h = 0; h < numHeads; h++) {
                    dt[h] = inProjData[dtOffset + h] * multDt;
                }
            } else {
                System.arraycopy(inProjData, dtOffset, dt, 0, numHeads);
            }

            // Depthwise Causal Conv1D update
            for (int c = 0; c < convDim; c++) {
                int base = c * dConv;
                float sum = 0.0f;
                if (dConv == 4) {
                    float b0 = convBuf[base + 1];
                    float b1 = convBuf[base + 2];
                    float b2 = convBuf[base + 3];
                    float b3 = inProjData[convInputOffset + c];
                    convBuf[base] = b0;
                    convBuf[base + 1] = b1;
                    convBuf[base + 2] = b2;
                    convBuf[base + 3] = b3;

                    if (convW != null) {
                        sum = b0 * convW[base] + b1 * convW[base + 1] + b2 * convW[base + 2] + b3 * convW[base + 3];
                    }
                } else {
                    for (int k = 0; k < dConv - 1; k++) {
                        convBuf[base + k] = convBuf[base + k + 1];
                    }
                    convBuf[base + dConv - 1] = inProjData[convInputOffset + c];

                    if (convW != null) {
                        for (int k = 0; k < dConv; k++) {
                            sum += convBuf[base + k] * convW[base + k];
                        }
                    }
                }
                if (convB != null) {
                    sum += convB[c];
                }
                convOut[c] = silu(sum);
            }

            // Split convOut into x, B, C
            System.arraycopy(convOut, 0, x, 0, intermediateSize);
            System.arraycopy(convOut, intermediateSize, B, 0, groupTimeStateSize);
            System.arraycopy(convOut, intermediateSize + groupTimeStateSize, C, 0, groupTimeStateSize);

            // SSM Recurrent update & Output computation
            int tokenScanOffset = t * intermediateSize;
            for (int h = 0; h < numHeads; h++) {
                float dtBiasVal = dtBiasArr != null ? dtBiasArr[h] : 0.0f;
                float dtVal = softplus(dt[h] + dtBiasVal);
                if (dtVal < timeStepMin) dtVal = timeStepMin;
                if (dtVal > timeStepMax) dtVal = timeStepMax;

                float aVal = weights.aVal != null ? weights.aVal[h] : (float) -Math.exp(aLogArr != null ? aLogArr[h] : 0.0f);
                float dA = (float) Math.exp(dtVal * aVal);

                float dVal = dArr != null ? dArr[h] : 1.0f;

                int g = h / headsPerGroup;
                int bBase = g * ssmStateSize;
                int cBase = g * ssmStateSize;

                for (int d = 0; d < headDim; d++) {
                    int row = h * headDim + d;
                    float xVal = x[row];
                    int ssmBase = row * ssmStateSize;
                    float ySum = 0.0f;
                    float dtx = dtVal * xVal;

                    for (int n = 0; n < ssmStateSize; n++) {
                        float bVal = B[bBase + n];
                        float cVal = C[cBase + n];
                        float newState = ssm[ssmBase + n] * dA + dtx * bVal;
                        ssm[ssmBase + n] = newState;
                        ySum += newState * cVal;
                    }

                    float y = ySum + dVal * xVal;
                    float siluG = silu(gate[row]);
                    scanOutputAll[tokenScanOffset + row] = y * siluG;
                }
            }
        }



        // 3. out_proj: [1, seqLen, intermediateSize] @ outProjWeight^T -> [1, seqLen, hiddenSize]
        AccelTensor scanTensor = AccelTensor.fromFloatArray(scanOutputAll, 1L, seqLen, intermediateSize);
        AccelTensor mambaOut;
        try {
            DirectForwardLinearContext linCtx = new DirectForwardLinearContext(
                    ctx.runtime(), ctx.traits(), config);
            DirectForwardLinearRequest linReq = DirectForwardLinearRequest.projection(
                    linCtx, scanTensor, weights.outProjWeight, null, "mamba_out_proj", null);
            mambaOut = DirectForwardLinearProjection.linear(linReq);
        } finally {
            scanTensor.close();
        }

        // 4. Apply ssm_out_multiplier if necessary
        if (ssmOutMult != 1.0f) {
            MemorySegment outSeg = mambaOut.dataPtr();
            long totalFloats = (long) seqLen * hiddenSize;
            for (long i = 0; i < totalFloats; i++) {
                long offset = i * Float.BYTES;
                float v = outSeg.get(ValueLayout.JAVA_FLOAT, offset);
                outSeg.set(ValueLayout.JAVA_FLOAT, offset, v * ssmOutMult);
            }
        }

        if (ctx.layerIdx() < 8 && Boolean.getBoolean("aqli.debug.mamba")) {
            float[] outData = mambaOut.toFloatArray();
            int lastTokenOff2 = (seqLen - 1) * hiddenSize;
            System.err.printf("[MAMBA-DBG] L%02d mambaOut: %.6f %.6f %.6f %.6f%n",
                    ctx.layerIdx(), outData[lastTokenOff2], outData[lastTokenOff2+1], outData[lastTokenOff2+2],
                    outData[lastTokenOff2+3]);
            System.err.flush();
        }

        return mambaOut;
    }
}
