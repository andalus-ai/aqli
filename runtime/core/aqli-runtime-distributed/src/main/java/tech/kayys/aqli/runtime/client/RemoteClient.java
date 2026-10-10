package tech.kayys.aqli.runtime.client;

import tech.kayys.aqli.runtime.plan.ExecutionPlan;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.alkhawarizm.core.tensor.Tensor;
import tech.kayys.aqli.runtime.kv.KVCache;
import tech.kayys.aqli.runtime.kv.KVCacheSnapshot;
import java.util.Map;

public interface RemoteClient {
    Map<String, Tensor> execute(
            ExecutionPlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session);

    // Added methods needed by RemoteExecutionProvider
    KVCacheSnapshot prefill(Tensor prompt, Tensor wqkv, int heads, int maxSeq);
    Tensor decode(Tensor token, Tensor wqkv, KVCache cache, int heads);
    
    // Added method needed by RemotePartitionExecutor
    Map<String, Tensor> executePartition(tech.kayys.aqli.runtime.distributed.Partition partition, Map<String, Tensor> inputs);
}
