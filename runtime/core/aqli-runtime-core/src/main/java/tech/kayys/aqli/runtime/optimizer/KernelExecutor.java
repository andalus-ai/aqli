package tech.kayys.aqli.runtime.optimizer;

import tech.kayys.alkhawarizm.core.tensor.Tensor;
import tech.kayys.alkhawarizm.core.tensor.DeviceType;
import tech.kayys.aqli.ir.GAttrValue;
import java.util.Map;

public interface KernelExecutor {
    Tensor[] execute(
            String kernelId,
            DeviceType device,
            Tensor[] inputs,
            Map<String, GAttrValue> attrs);
}
