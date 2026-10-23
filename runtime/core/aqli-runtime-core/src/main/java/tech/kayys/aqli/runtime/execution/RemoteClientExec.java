package tech.kayys.aqli.runtime.exec;

import tech.kayys.aljabr.core.tensor.Tensor;
import tech.kayys.aqli.runtime.kv.*;

public interface RemoteClientExec {
    KVCacheSnapshot prefill(
            Tensor prompt,
            Tensor wqkv,
            int heads,
            int maxSeq);

    Tensor decode(
            Tensor token,
            Tensor wqkv,
            KVCache cache,
            int heads);
}