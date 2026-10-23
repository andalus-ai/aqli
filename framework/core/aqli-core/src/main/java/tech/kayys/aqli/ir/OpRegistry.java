package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.*;

public final class OpRegistry {

    private final Map<String, OpDescriptor> ops = new HashMap<>();

    public void register(OpDescriptor op) {
        ops.put(op.id().key(), op);
    }

    public OpDescriptor get(OpId id) {
        OpDescriptor op = ops.get(id.key());
        if (op == null) {
            throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Unknown op: " + id.key());
        }
        return op;
    }
}
