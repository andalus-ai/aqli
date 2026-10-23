package tech.kayys.aqli.adapter;


import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class SafeTensorAdapter implements WeightAdapter {
    private final Map<String, Tensor> weights;

    public SafeTensorAdapter(Map<String, Tensor> weights) {
        this.weights = weights;
    }

    @Override
    public Tensor getWeight(String name) {
        return weights.get(name);
    }

    @Override
    public int numLayers() {
        return 32; // parse from metadata
    }

    @Override
    public int hiddenSize() {
        return 4096;
    }

    @Override
    public int numHeads() {
        return 32;
    }
}