package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GValueId {

    private final String id;

    public GValueId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof GValueId other) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}