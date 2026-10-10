package tech.kayys.aqli.runtime.plan;

import tech.kayys.aqli.ir.*;
import tech.kayys.alkhawarizm.core.tensor.DeviceType;

/**
 * IR → Compiler → ExecutionPlan
 * ↓
 * Execution Planner
 * ↓
 * 🔥 Cost Model + Placement + Kernel Selection
 * ↓
 * Executable Plan (annotated)
 * ↓
 * ExecutionEngine
 */
public final class PlannedStep {
    public final GOp op;
    public final DeviceType device;
    public final String kernelId;

    public PlannedStep(GOp op, DeviceType device, String kernelId) {
        this.op = op;
        this.device = device;
        this.kernelId = kernelId;
    }
}
