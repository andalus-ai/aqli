package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionRouteMetadataTest {

    @Test
    void responseMetadataOverlaysForcedMetadata() {
        Map<String, Object> forced = new LinkedHashMap<>();
        forced.put("effective_provider", "safetensor");
        forced.put("requested_provider", "libtorch");
        forced.put("route_source", "cli");

        Map<String, Object> response = Map.of(
                "effective_provider", "java",
                "runner", "direct-safetensor");

        Map<String, Object> result = ExecutionRouteMetadata.effective(forced, response);

        assertEquals("java", result.get("effective_provider"));
        assertEquals("libtorch", result.get("requested_provider"));
        assertEquals("direct-safetensor", result.get("runner"));
        assertEquals("cli", result.get("route_source"));
    }

    @Test
    void returnsForcedMetadataWhenResponseMetadataIsEmpty() {
        Map<String, Object> forced = Map.of("effective_provider", "safetensor");

        assertSame(forced, ExecutionRouteMetadata.effective(forced, Map.of()));
        assertSame(forced, ExecutionRouteMetadata.effective(forced, null));
    }

    @Test
    void returnsResponseMetadataWhenForcedMetadataIsEmpty() {
        Map<String, Object> response = Map.of("effective_provider", "java");

        assertSame(response, ExecutionRouteMetadata.effective(Map.of(), response));
        assertSame(response, ExecutionRouteMetadata.effective(null, response));
    }

    @Test
    void bridgeMetadataRetainsEstablishedCompatibilityFields() {
        Map<String, Object> metadata = ExecutionRouteMetadata.bridgeMetadata();

        assertEquals("libtorch", metadata.get("requested_provider"));
        assertEquals("safetensor", metadata.get("effective_provider"));
        assertEquals("cli_libtorch_to_safetensor", metadata.get("provider_bridge_mode"));
        assertEquals("raw_safetensor_checkpoint", metadata.get("provider_bridge_reason"));
    }

    @Test
    void mergeIgnoresNullInputsAndKeepsOverlayPrecedence() {
        assertEquals(Map.of("value", 2), ExecutionRouteMetadata.merge(
                Map.of("value", 1), Map.of("value", 2)));
        assertEquals(Map.of("value", 1), ExecutionRouteMetadata.merge(
                Map.of("value", 1), null));
        assertEquals(Map.of("value", 2), ExecutionRouteMetadata.merge(
                null, Map.of("value", 2)));
    }
}
