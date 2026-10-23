package tech.kayys.aqli.adapter;


import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import tech.kayys.aljabr.core.tensor.ModelWeightLoader;
import java.nio.file.Path;

public final class GGUFModelLoader implements ModelWeightLoader {
    @Override
    public boolean supports(Path path) {
        return path.toString().endsWith(".gguf");
    }

    @Override
    public WeightAdapter load(Path path) {
        return new GGUFAdapter();
    }
}
