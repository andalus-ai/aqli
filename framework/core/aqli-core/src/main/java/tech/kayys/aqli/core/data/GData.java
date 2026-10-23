package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import tech.kayys.aljabr.core.tensor.Tensor;

/**
 * Unified data contract for all Aqli runtime data types.
 */
public sealed interface GData permits GTensor, GScalar, GHandle {
    String id();
}
