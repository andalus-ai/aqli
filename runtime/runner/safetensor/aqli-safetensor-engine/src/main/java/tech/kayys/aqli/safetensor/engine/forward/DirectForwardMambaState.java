/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import java.util.Arrays;

/**
 * State cache for Mamba / SSM layers across prefill and decode steps.
 *
 * Stores depthwise causal conv1d buffer and SSM recurrent states per layer.
 */
public final class DirectForwardMambaState {
    private final int numLayers;
    private final int convDim;
    private final int dConv;
    private final int numHeads;
    private final int headDim;
    private final int ssmStateSize;

    // Layer -> flattened conv state: [convDim * dConv]
    private final float[][] convStates;

    // Layer -> flattened recurrent state: [numHeads * headDim * ssmStateSize]
    private final float[][] ssmStates;

    public DirectForwardMambaState(int numLayers, int convDim, int dConv, int numHeads, int headDim, int ssmStateSize) {
        this.numLayers = numLayers;
        this.convDim = convDim;
        this.dConv = dConv;
        this.numHeads = numHeads;
        this.headDim = headDim;
        this.ssmStateSize = ssmStateSize;

        this.convStates = new float[numLayers][convDim * dConv];
        this.ssmStates = new float[numLayers][numHeads * headDim * ssmStateSize];
    }

    public float[] convState(int layerIdx) {
        return convStates[layerIdx];
    }

    public float[] ssmState(int layerIdx) {
        return ssmStates[layerIdx];
    }

    public int convDim() {
        return convDim;
    }

    public int dConv() {
        return dConv;
    }

    public int numHeads() {
        return numHeads;
    }

    public int headDim() {
        return headDim;
    }

    public int ssmStateSize() {
        return ssmStateSize;
    }

    public void reset() {
        for (float[] conv : convStates) {
            Arrays.fill(conv, 0.0f);
        }
        for (float[] ssm : ssmStates) {
            Arrays.fill(ssm, 0.0f);
        }
    }
}
