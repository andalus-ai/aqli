package tech.kayys.aqli.runtime.distributed;

import tech.kayys.aljabr.core.tensor.Tensor;
import tech.kayys.aljabr.core.tensor.DeviceType;

import java.util.*;

public final class DistributedExecutor {
    private final Map<DeviceType, PartitionExecutor> executors;

    public DistributedExecutor(Map<DeviceType, PartitionExecutor> executors) {
        this.executors = executors;
    }

    public Map<String, Tensor> run(
            PartitionedPlan plan,
            Map<String, Tensor> inputs) {
        Map<String, Tensor> data = new HashMap<>(inputs);
        for (Partition p : plan.partitions()) {
            PartitionExecutor exec = executors.get(p.device());
            data = exec.execute(p, data);
        }
        return data;
    }
}