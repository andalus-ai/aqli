package tech.kayys.aqli.cli.commands;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combines route metadata reported by the selected runner with route metadata known by
 * the CLI orchestration layer.
 *
 * <p>Runner metadata overlays forced metadata so actual execution details take precedence.
 * This utility contains no routing or provider-selection policy.</p>
 */
final class ExecutionRouteMetadata {

    private ExecutionRouteMetadata() {}

    static Map<String, Object> effective(
            Map<String, Object> forcedMetadata,
            Map<String, Object> responseMetadata) {
        if (responseMetadata == null || responseMetadata.isEmpty()) {
            return forcedMetadata;
        }
        if (forcedMetadata == null || forcedMetadata.isEmpty()) {
            return responseMetadata;
        }
        return merge(forcedMetadata, responseMetadata);
    }

    static Map<String, Object> bridgeMetadata() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("requested_provider", "libtorch");
        route.put("effective_provider", "safetensor");
        route.put("provider_bridge_mode", "cli_libtorch_to_safetensor");
        route.put("provider_bridge_reason", "raw_safetensor_checkpoint");
        return route;
    }

    static Map<String, Object> merge(
            Map<String, Object> baseMetadata,
            Map<String, Object> overlayMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseMetadata != null && !baseMetadata.isEmpty()) {
            merged.putAll(baseMetadata);
        }
        if (overlayMetadata != null && !overlayMetadata.isEmpty()) {
            merged.putAll(overlayMetadata);
        }
        return merged;
    }
}
