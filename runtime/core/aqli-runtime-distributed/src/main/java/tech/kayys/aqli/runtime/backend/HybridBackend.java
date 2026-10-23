package tech.kayys.aqli.runtime.backend;

import tech.kayys.aqli.runtime.plan.ExecutionPlan;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.aljabr.core.tensor.Tensor;
import java.util.Map;

public final class HybridBackend implements ExecutionBackend {
    private final ExecutionBackend local;
    private final ExecutionBackend remote;

    public HybridBackend(ExecutionBackend local, ExecutionBackend remote) {
        this.local = local;
        this.remote = remote;
    }

    @Override
    public Map<String, Tensor> execute(
            ExecutionPlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session) {
        if (isPrefill(inputs)) {
            return local.execute(plan, inputs, session);
        } else {
            return remote.execute(plan, inputs, session);
        }
    }

    private boolean isPrefill(Map<String, Tensor> inputs) {
        // heuristic / flag-based
        return inputs.size() > 1;
    }
}
