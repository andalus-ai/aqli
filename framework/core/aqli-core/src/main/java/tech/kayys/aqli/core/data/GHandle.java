package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class GHandle implements GData {
    private final String id;
    private final Object ref;

    public GHandle(String id, Object ref) {
        this.id = id;
        this.ref = ref;
    }

    public Object ref() {
        return ref;
    }

    @Override
    public String id() {
        return id;
    }
}
