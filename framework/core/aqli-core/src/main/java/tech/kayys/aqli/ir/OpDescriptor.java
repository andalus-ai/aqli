package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class OpDescriptor {
    private final OpId id;

    public OpDescriptor(OpId id) {
        this.id = id;
    }

    public OpId id() {
        return id;
    }
}