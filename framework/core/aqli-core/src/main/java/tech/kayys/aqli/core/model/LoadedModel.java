package tech.kayys.aqli.core.model;

import tech.kayys.aqli.ir.GGraph;
import tech.kayys.aqli.core.weight.WeightStore;
import tech.kayys.alkhawarizm.spi.spec.*;
import tech.kayys.alkhawarizm.core.tensor.DeviceType;
import tech.kayys.alkhawarizm.core.model.ModelFormat;

public final class LoadedModel {
    public final GGraph graph;
    public final WeightStore weights;

    public LoadedModel(GGraph graph, WeightStore weights) {
        this.graph = graph;
        this.weights = weights;
    }
}