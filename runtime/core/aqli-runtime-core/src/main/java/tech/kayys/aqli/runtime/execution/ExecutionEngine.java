package tech.kayys.aqli.runtime.execution;

import tech.kayys.alkhawarizm.core.tensor.Tensor;
import tech.kayys.aqli.runtime.plan.PlannedStep;
import tech.kayys.aqli.runtime.plan.ExecutablePlan;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.aqli.runtime.plan.ExecutablePlan;
import tech.kayys.aqli.runtime.plan.PlannedStep;
import tech.kayys.aqli.runtime.optimizer.KernelExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Executes a pre-planned sequence of kernels.
 *
 * <p>
 * The planner is responsible for choosing a kernel and device. This class
 * enforces the runtime contract: all inputs must exist, every planned step must
 * produce exactly the declared outputs, and cancellation/interruption must not
 * be silently ignored.
 * </p>
 */
public final class ExecutionEngine {
    private static final long PAUSE_POLL_MILLIS = 10L;

    private final KernelExecutor executor;

    public ExecutionEngine(KernelExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Map<String, Tensor> run(
            ExecutablePlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(plan.steps, "plan.steps");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(session.controller, "session.controller");

        Map<String, Tensor> values = new HashMap<>();
        for (Map.Entry<String, Tensor> entry : inputs.entrySet()) {
            String id = Objects.requireNonNull(entry.getKey(), "input id");
            Tensor tensor = Objects.requireNonNull(entry.getValue(), "input tensor for '" + id + "'");
            values.put(id, tensor);
        }

        checkControl(session);
        for (int stepIndex = 0; stepIndex < plan.steps.size(); stepIndex++) {
            checkControl(session);
            PlannedStep step = Objects.requireNonNull(
                    plan.steps.get(stepIndex), "plan step at index " + stepIndex);
            GOp op = Objects.requireNonNull(step.op, "operation at step " + stepIndex);
            String opName = op.name() == null || op.name().isBlank()
                    ? op.opType()
                    : op.name();

            if (step.kernelId == null || step.kernelId.isBlank()) {
                throw new IllegalStateException("Missing kernel id for operation '" + opName + "'");
            }
            if (step.device == null) {
                throw new IllegalStateException("Missing execution device for operation '" + opName + "'");
            }
            if (op.inputs() == null || op.outputs() == null || op.attrs() == null) {
                throw new IllegalStateException("Incomplete operation definition for '" + opName + "'");
            }

            List<Tensor> inputTensors = new ArrayList<>(op.inputs().size());
            for (GValueRef inputRef : op.inputs()) {
                if (inputRef == null || inputRef.id() == null || inputRef.id().id() == null) {
                    throw new IllegalStateException("Invalid input reference for operation '" + opName + "'");
                }
                String inputId = inputRef.id().id();
                Tensor inputTensor = values.get(inputId);
                if (inputTensor == null) {
                    throw new IllegalStateException(
                            "Missing input '" + inputId + "' for operation '" + opName
                                    + "' at plan step " + stepIndex);
                }
                inputTensors.add(inputTensor);
            }

            checkControl(session);
            Tensor[] outputs = executor.execute(
                    step.kernelId,
                    step.device,
                    inputTensors.toArray(Tensor[]::new),
                    op.attrs());

            int expectedOutputs = op.outputs().size();
            if (outputs == null) {
                throw new IllegalStateException(
                        "Kernel '" + step.kernelId + "' returned null outputs for operation '" + opName + "'");
            }
            if (outputs.length != expectedOutputs) {
                throw new IllegalStateException(
                        "Kernel '" + step.kernelId + "' for operation '" + opName
                                + "' returned " + outputs.length + " outputs; expected " + expectedOutputs);
            }

            for (int i = 0; i < expectedOutputs; i++) {
                GValueId outputId = op.outputs().get(i);
                if (outputId == null || outputId.id() == null || outputId.id().isBlank()) {
                    throw new IllegalStateException(
                            "Invalid output reference at index " + i + " for operation '" + opName + "'");
                }
                Tensor output = outputs[i];
                if (output == null) {
                    throw new IllegalStateException(
                            "Kernel '" + step.kernelId + "' returned null for output '"
                                    + outputId.id() + "' of operation '" + opName + "'");
                }
                values.put(outputId.id(), output);
            }
        }

        checkControl(session);
        return values;
    }

    private static void checkControl(ExecutionSession session) {
        var controller = session.controller;
        while (controller.isPaused()) {
            if (Thread.currentThread().isInterrupted()) {
                throw interruptedExecution();
            }
            try {
                Thread.sleep(PAUSE_POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interruptedExecution();
            }
        }

        if (Thread.currentThread().isInterrupted()) {
            throw interruptedExecution();
        }
        if (controller.isCancelled()) {
            throw new CancellationException("Execution was cancelled");
        }
        if (controller.isShutdown()) {
            throw new IllegalStateException("Execution was stopped because the runtime is shutting down");
        }
    }

    private static CancellationException interruptedExecution() {
        return new CancellationException("Execution interrupted");
    }
}
