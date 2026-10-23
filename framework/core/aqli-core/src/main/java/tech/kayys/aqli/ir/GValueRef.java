package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.Objects;

public final class GValueRef {
    private final GValueId id;

    public GValueRef(GValueId id) {
        this.id = Objects.requireNonNull(id);
    }

    public GValueId id() {
        return id;
    }
}