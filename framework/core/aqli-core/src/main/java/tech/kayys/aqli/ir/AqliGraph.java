package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.List;

/**
 * [ Model Formats ]
 * (safetensors / gguf / onnx / tflite / torch / etc.)
 * ↓
 * [ Loaders ]
 * (format-specific parsing)
 * ↓
 * [ Adapters ]
 * (normalize → unified structure)
 * ↓
 * 🔥 [ AQLI IR ] 🔥 ← THIS IS THE CORE YOU ARE MISSING
 * ↓
 * [ Optimizer / Compiler ]
 * (fusion, quantization, layout)
 * ↓
 * [ Runtime ]
 * (your current work: tensor + kernels + kv + adaptive)
 * ↓
 * [ Backend ]
 * (CPU / GPU / Metal / etc.)
 * 
 * class MatMulNode implements AqliNode {}
 * class AttentionNode implements AqliNode {}
 * class Conv2DNode implements AqliNode {}
 * class RMSNormNode implements AqliNode {}
 */
public final class AqliGraph {

    private final List<AqliNode> nodes;

    public AqliGraph(List<AqliNode> nodes) {
        this.nodes = nodes;
    }

    public List<AqliNode> nodes() {
        return nodes;
    }
}