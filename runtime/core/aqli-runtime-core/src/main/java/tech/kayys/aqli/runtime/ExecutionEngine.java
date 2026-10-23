package tech.kayys.aqli.runtime;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aqli.runtime.plan.*;
import tech.kayys.aqli.core.weight.WeightStore;
import tech.kayys.aqli.runtime.control.*;
import tech.kayys.aljabr.core.tensor.Tensor;
import tech.kayys.aqli.ir.GOp;
import tech.kayys.aqli.ir.GValueId;
import tech.kayys.aqli.ir.GValueRef;
import tech.kayys.aqli.ir.OpKernel;
import tech.kayys.aqli.ir.OpRegistry;
import tech.kayys.aqli.ir.OpId;

import java.util.*;

public final class ExecutionEngine {
    private final OpRegistry registry;

    public ExecutionEngine(OpRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Tensor> run(
            ExecutionPlan plan,
            Map<String, Tensor> inputs,
            WeightStore weights) {

        Map<String, Tensor> values = new HashMap<>(inputs);
        
        for (PlanStep step : plan.steps) {
            Tensor[] in = step.op.inputs().stream()
                    .map(ref -> {
                        Tensor t = values.get(ref.id().id());
                        if (t != null) return t;
                        return weights.get(ref.id().id());
                    })
                    .toArray(Tensor[]::new);

            // OpKernel might need to be cast or typed correctly based on OpRegistry
            OpId opId = new OpId(step.op.opType());
            // This is still a bit stubbed as OpRegistry returns OpDescriptor, not OpKernel
            // but for now we follow the existing pattern
            Object obj = registry.get(opId);
            if (obj instanceof OpKernel kernel) {
                Tensor[] out = (Tensor[]) kernel.compute(Arrays.asList(in), step.op.attrs());
                for (int i = 0; i < step.op.outputs().size(); i++) {
                    String outId = step.op.outputs().get(i).id();
                    values.put(outId, out[i]);
                }
            }
        }

        Map<String, Tensor> outputs = new HashMap<>();
        for (Map.Entry<GValueId, Integer> entry : plan.slotMap.entrySet()) {
            outputs.put(entry.getKey().id(), values.get(entry.getKey().id()));
        }
        return outputs;
    }

    private void handleControl(ExecutionSession session) {
        ExecutionController ctrl = session.controller;
        if (ctrl.isCancelled()) {
            throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Execution cancelled");
        }
        if (ctrl.isShutdown()) {
            throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Execution shutdown");
        }
        while (ctrl.isPaused()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
    }
}