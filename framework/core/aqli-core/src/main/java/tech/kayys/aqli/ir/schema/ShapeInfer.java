package tech.kayys.aqli.ir.schema;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aqli.ir.*;
import java.util.*;

public interface ShapeInfer {
    Shape[] infer(
            List<GValue> inputs,
            Map<String, GAttrValue> attrs);
}