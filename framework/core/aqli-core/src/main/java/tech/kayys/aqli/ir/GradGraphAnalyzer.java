package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import tech.kayys.aqli.ir.*;
import java.util.*;

public final class GradGraphAnalyzer {
    public static Map<GValueId, Integer> buildUseCount(GGraph graph) {
        Map<GValueId, Integer> useCount = new HashMap<>();
        for (GOp op : graph.ops()) {
            for (GValueRef in : op.inputs()) {
                useCount.merge(in.id(), 1, Integer::sum);
            }
        }
        return useCount;
    }

    public static Map<GValueId, GOp> buildProducerMap(GGraph graph) {
        Map<GValueId, GOp> map = new HashMap<>();
        for (GOp op : graph.ops()) {
            for (GValueId out : op.outputs()) {
                map.put(out, op);
            }
        }
        return map;
    }
}