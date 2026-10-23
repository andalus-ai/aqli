package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GGraph {
    private final List<GOp> ops;
    private final List<GValueId> inputs;
    private final List<GValueId> outputs;

    public GGraph(
            List<GOp> ops,
            List<GValueId> inputs,
            List<GValueId> outputs) {
        this.ops = ops;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public List<GOp> ops() {
        return ops;
    }

    public List<GValueId> inputs() {
        return inputs;
    }

    public List<GValueId> outputs() {
        return outputs;
    }
}