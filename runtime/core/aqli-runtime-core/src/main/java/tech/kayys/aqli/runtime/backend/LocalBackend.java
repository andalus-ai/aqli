package tech.kayys.aqli.runtime.backend;

import tech.kayys.aqli.runtime.*;
import tech.kayys.aqli.runtime.plan.*;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.aljabr.core.tensor.Tensor;
import java.util.Map;

public final class LocalBackend implements ExecutionBackend {
    private final ExecutionEngine engine;

    public LocalBackend(ExecutionEngine engine) {
        this.engine = engine;
    }

    @Override
    public Map<String, Tensor> execute(
            ExecutionPlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session) {
        return engine.run(plan, inputs, null); // Pass null for WeightStore for now
    }
}