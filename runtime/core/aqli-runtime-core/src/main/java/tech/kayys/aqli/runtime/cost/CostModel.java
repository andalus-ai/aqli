package tech.kayys.aqli.runtime.cost;

import tech.kayys.aqli.ir.*;
import tech.kayys.alkhawarizm.core.tensor.DeviceType;
import tech.kayys.aqli.runtime.kernel.*;

public interface CostModel {
    double estimate(
            GOp op,
            KernelCandidate kernel,
            DeviceType device);
}
