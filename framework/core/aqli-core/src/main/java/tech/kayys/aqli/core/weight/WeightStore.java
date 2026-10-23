package tech.kayys.aqli.core.weight;

import tech.kayys.aljabr.core.tensor.Tensor;

/**
 * 
 * GGraph
 * ↓
 * GValue (id only)
 * ↓
 * WeightStore (external tensor storage)
 * 
 * This should avoiding:
 * ❌ huge memory duplication
 * ❌ impossible to stream / lazy load
 * ❌ breaks distributed runtime
 * ❌ kills quantization flexibility
 * :
 */
public interface WeightStore {
    Tensor get(String key);

    boolean contains(String key);
}