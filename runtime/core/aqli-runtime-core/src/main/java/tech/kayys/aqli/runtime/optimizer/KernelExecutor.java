package tech.kayys.aqli.runtime.optimizer;

import tech.kayys.aljabr.core.tensor.Tensor;
import tech.kayys.aljabr.core.tensor.DeviceType;
import tech.kayys.aqli.ir.GAttrValue;
import java.util.Map;

public interface KernelExecutor {
    Tensor[] execute(
            String kernelId,
            DeviceType device,
            Tensor[] inputs,
            Map<String, GAttrValue> attrs);
}
