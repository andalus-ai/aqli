/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import static tech.kayys.aqli.safetensor.engine.forward.DirectForwardTensorOps.tensorSummary;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aqli.safetensor.engine.generation.DirectInferenceProfiler;
import tech.kayys.aljabr.spi.model.ModelConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class DirectForwardFfnFastPathTrace {
    private static final String TRACE_FFN_FAST_PATH_PROPERTY =
            "aqli.safetensor.trace_ffn_fast_path";
    private static final boolean ENABLED = Boolean.getBoolean(TRACE_FFN_FAST_PATH_PROPERTY);
    private static final Set<String> TRACED_DECISIONS = ConcurrentHashMap.newKeySet();

    private DirectForwardFfnFastPathTrace() {
    }

    static boolean isEnabled() {
        return ENABLED;
    }

    static void trace(String path,
                      String decision,
                      ModelConfig config,
                      AccelTensor input,
                      AccelTensor gateW,
                      AccelTensor upW,
                      AccelTensor downW) {
        DirectInferenceProfiler.recordFfnPath(path + ":" + decision);
        if (!ENABLED) {
            return;
        }
        String key = path + "|" + decision + "|" + tensorSummary(input)
                + "|" + tensorSummary(gateW) + "|" + tensorSummary(upW) + "|" + tensorSummary(downW);
        if (!TRACED_DECISIONS.add(key)) {
            return;
        }
        System.err.printf("[aqli-ffn] path=%s decision=%s model=%s input=%s gate=%s up=%s down=%s%n",
                path,
                decision,
                config != null ? config.modelType() : "unknown",
                tensorSummary(input),
                tensorSummary(gateW),
                tensorSummary(upW),
                tensorSummary(downW));
    }
}
