package tech.kayys.aqli.runtime.plan;

import tech.kayys.aqli.ir.*;
import java.util.List;

public final class PlanStep {
    public final GOp op;
    public final List<GValueId> inputs;
    public final List<GValueId> outputs;

    public PlanStep(GOp op,
            List<GValueId> inputs,
            List<GValueId> outputs) {
        this.op = op;
        this.inputs = inputs;
        this.outputs = outputs;
    }
}