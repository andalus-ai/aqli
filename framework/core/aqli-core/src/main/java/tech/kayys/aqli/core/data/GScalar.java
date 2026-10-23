package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class GScalar implements GData {
    private final String id;
    private final float value;

    public GScalar(String id, float value) {
        this.id = id;
        this.value = value;
    }

    public float value() {
        return value;
    }

    @Override
    public String id() {
        return id;
    }
}
