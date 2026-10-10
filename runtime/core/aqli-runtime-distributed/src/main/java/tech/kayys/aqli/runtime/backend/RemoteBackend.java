package tech.kayys.aqli.runtime.backend;

import tech.kayys.aqli.runtime.plan.ExecutionPlan;
import tech.kayys.aqli.runtime.control.ExecutionSession;
import tech.kayys.alkhawarizm.core.tensor.Tensor;
import tech.kayys.aqli.runtime.client.RemoteClient;
import java.util.Map;

public final class RemoteBackend implements ExecutionBackend {
    private final RemoteClient client;

    public RemoteBackend(RemoteClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Tensor> execute(
            ExecutionPlan plan,
            Map<String, Tensor> inputs,
            ExecutionSession session) {
        return client.execute(plan, inputs, session);
    }
}
