package tech.kayys.aqli.ir;

import tech.kayys.alkhawarizm.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.alkhawarizm.core.tensor.*;

import tech.kayys.alkhawarizm.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GMeta {
    public final String sourceFormat;
    public final String modelName;
    public final Map<String, String> tags;

    public GMeta(String sourceFormat, String modelName, Map<String, String> tags) {
        this.sourceFormat = sourceFormat;
        this.modelName = modelName;
        this.tags = tags;
    }
}