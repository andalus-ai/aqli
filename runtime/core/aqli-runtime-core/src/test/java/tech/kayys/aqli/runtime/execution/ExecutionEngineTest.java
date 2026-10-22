package tech.kayys.aqli.runtime.execution;

import org.junit.jupiter.api.Test;
import tech.kayys.alkhawarizm.core.tensor.DeviceType;
import tech.kayys.alkhawarizm.core.tensor.Tensor;
import tech.kayys.aqli.ir.GOp;
import tech.kayys.aqli.ir.GValueId;
import tech.kayys.aqli.ir.GValueRef;
import tech.kayys.aqli.runtime.control.ExecutionController;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.aqli.runtime.optimizer.KernelExecutor;
import tech.kayys.aqli.runtime.plan.ExecutablePlan;
import tech.kayys.aqli.runtime.plan.PlannedStep;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEngineTest {

    @Test
    void rejectsMissingInputBeforeInvokingKernel() {
        AtomicInteger calls = new AtomicInteger();
        ExecutionEngine engine = new ExecutionEngine((kernelId, device, inputs, attrs) -> {
            calls.incrementAndGet();
            return new Tensor[]{tensor()};
        });
        ExecutablePlan plan = plan(operation("input", "output"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> engine.run(plan, Map.of(), session()));

        assertTrue(error.getMessage().contains("Missing input 'input'"));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsKernelOutputCountMismatch() {
        ExecutionEngine engine = new ExecutionEngine((kernelId, device, inputs, attrs) -> new Tensor[0]);
        ExecutablePlan plan = plan(operation("input", "output"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> engine.run(plan, Map.of("input", tensor()), session()));

        assertTrue(error.getMessage().contains("expected 1"));
    }

    @Test
    void rejectsNullKernelOutput() {
        ExecutionEngine engine = new ExecutionEngine((kernelId, device, inputs, attrs) -> new Tensor[]{null});
        ExecutablePlan plan = plan(operation("input", "output"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> engine.run(plan, Map.of("input", tensor()), session()));

        assertTrue(error.getMessage().contains("returned null"));
    }

    @Test
    void cancellationStopsExecutionBeforeFirstKernel() {
        AtomicInteger calls = new AtomicInteger();
        KernelExecutor executor = (kernelId, device, inputs, attrs) -> {
            calls.incrementAndGet();
            return new Tensor[]{tensor()};
        };
        ExecutionEngine engine = new ExecutionEngine(executor);
        ExecutionController controller = new ExecutionController();
        controller.cancel();

        assertThrows(
                CancellationException.class,
                () -> engine.run(plan(operation("input", "output")), Map.of("input", tensor()),
                        new ExecutionSession(controller)));
        assertEquals(0, calls.get());
    }

    private static ExecutablePlan plan(GOp op) {
        return new ExecutablePlan(List.of(new PlannedStep(op, DeviceType.CPU, "test-kernel")));
    }

    private static GOp operation(String inputId, String outputId) {
        return new GOp(
                "test-op",
                "test-op",
                List.of(new GValueRef(new GValueId(inputId))),
                List.of(new GValueId(outputId)),
                Map.of());
    }

    private static ExecutionSession session() {
        return new ExecutionSession(new ExecutionController());
    }

    private static Tensor tensor() {
        return (Tensor) Proxy.newProxyInstance(
                Tensor.class.getClassLoader(),
                new Class<?>[]{Tensor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "test-tensor";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive() || returnType == void.class) {
                        return null;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return 0;
                });
    }
}
