package tech.kayys.aqli.inference.llamacpp;

import java.util.LinkedHashSet;
import java.util.Set;

final class LlamaCppOptimizationDetector {

    private LlamaCppOptimizationDetector() {
    }

    static Set<String> detectFeatures() {
        Set<String> features = new LinkedHashSet<>();
        if (isPresent("tech.kayys.aqli.cache.PromptCacheLookupPlugin")) {
            features.add("prompt_cache");
        }
        if (isPresent("tech.kayys.aqli.kvcache.PagedKVCacheManager")) {
            features.add("paged_kv_cache");
        }
        if (isPresent("tech.kayys.aqli.kernel.paged.PagedAttentionBinding")) {
            features.add("paged_attention");
        }
        if (isPresent("tech.kayys.aqli.prefilldecode.PrefillDecodeDisaggService")) {
            features.add("prefill_decode_disagg");
        }
        if (isPresent("tech.kayys.aqli.hybridattn.HybridAttentionGdnRunner")) {
            features.add("hybrid_attention");
        }
        if (isPresent("tech.kayys.aqli.flashattn.FlashAttention4Runner")) {
            features.add("flash_attention4");
        }
        return features;
    }

    static boolean hasOptimizationModules() {
        return !detectFeatures().isEmpty();
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, LlamaCppOptimizationDetector.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
