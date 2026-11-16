package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Applies the CLI response-cache bypass option to an inference request.
 *
 * <p>This controls response caching only. It is intentionally separate from
 * model, prefix, and KV-cache lifecycle or quantization configuration.</p>
 */
public final class RuntimeResponseCacheRequestConfiguration {

    private RuntimeResponseCacheRequestConfiguration() {
    }

    public static void apply(InferenceRequest.Builder request, boolean bypassCache) {
        request.cacheBypass(bypassCache);
    }
}
