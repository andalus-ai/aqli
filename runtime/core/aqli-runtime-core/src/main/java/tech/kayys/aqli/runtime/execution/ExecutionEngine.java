package tech.kayys.aqli.runtime.execution;

import tech.kayys.aljabr.core.tensor.Tensor;
import tech.kayys.aqli.runtime.plan.PlannedStep;
import tech.kayys.aqli.runtime.plan.ExecutablePlan;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.aqli.runtime.optimizer.KernelExecutor;
import java.util.*;

public final class ExecutionEngine {
    private final KernelExecutor executor;

    public ExecutionEngine(KernelExecutor executor) {
        this.executor = executor;
    }

    public Map<String, Tensor> run(
            ExecutablePlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session) {
        Map<String, Tensor> values = new HashMap<>(inputs);
        for (PlannedStep step : plan.steps) {
            Tensor[] in = step.op.inputs().stream()
                    .map(ref -> values.get(ref.id().id()))
                    .toArray(Tensor[]::new);
            Tensor[] out = executor.execute(
                    step.kernelId,
                    step.device,
                    in,
                    step.op.attrs());
            for (int i = 0; i < step.op.outputs().size(); i++) {
                values.put(step.op.outputs().get(i).id(), out[i]);
            }
        }
        return values;
    }
}
