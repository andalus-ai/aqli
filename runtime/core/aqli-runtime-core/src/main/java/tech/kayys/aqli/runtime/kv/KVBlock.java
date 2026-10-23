package tech.kayys.aqli.runtime.kv;

import tech.kayys.aljabr.core.tensor.Tensor;

public final class KVBlock {
    public Tensor key;
    public Tensor value;
    public int position;

    public KVBlock(Tensor key, Tensor value, int position) {
        this.key = key;
        this.value = value;
        this.position = position;
    }
}