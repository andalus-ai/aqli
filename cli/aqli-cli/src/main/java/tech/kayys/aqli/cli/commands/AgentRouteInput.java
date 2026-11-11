package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed CLI input for route-template aware agent commands.
 */
record AgentRouteInput(
        String modelId,
        String surface,
        String featureProfile,
        Map<String, Object> request,
        Boolean discoverMcpTools,
        Boolean mcpDiscoveryRequired,
        Boolean validateTools,
        Boolean toolValidationRequired,
        Boolean validateRequest,
        Boolean requestValidationRequired,
        Boolean openAiToolCompatibility,
        Boolean enabledOnly,
        String preflightPolicy,
        Boolean requireRouteMatch,
        Boolean failOnWarnings) {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    static AgentRouteInput generated(Map<String, Object> request) {
        return new AgentRouteInput(
                null,
                null,
                null,
                request,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    static AgentRouteInput parse(String jsonText, ObjectMapper mapper, String label) throws IOException {
        JsonNode root = mapper.readTree(jsonText);
        if (!root.isObject()) {
            throw new IllegalArgumentException(label + " JSON must be an object.");
        }
        JsonNode requestNode = root.path("request").isObject() ? root.path("request") : root;
        return new AgentRouteInput(
                firstNonBlank(nodeText(root.get("model")), nodeText(root.get("model_id")), nodeText(requestNode.get("model"))),
                firstNonBlank(nodeText(root.get("surface")), nodeText(requestNode.get("surface"))),
                firstNonBlank(
                        nodeText(root.get("feature_profile")),
                        nodeText(root.get("featureProfile")),
                        nodeText(root.get("profile"))),
                mapper.convertValue(requestNode, MAP_TYPE),
                bool(root, "discover_mcp_tools", "discoverMcpTools"),
                bool(root, "mcp_discovery_required", "mcpDiscoveryRequired"),
                bool(root, "validate_tools", "validateTools"),
                bool(root, "tool_validation_required", "toolValidationRequired"),
                bool(root, "validate_request", "validateRequest"),
                bool(root, "request_validation_required", "requestValidationRequired"),
                bool(root, "openai_tool_compatibility", "openAiToolCompatibility"),
                bool(root, "enabled_only", "enabledOnly"),
                policyProfile(root),
                policyBoolean(root,
                        "route_match_required",
                        "routeMatchRequired",
                        "require_route_match",
                        "requireRouteMatch"),
                policyBoolean(root,
                        "warnings_blocking",
                        "warningsBlocking",
                        "fail_on_warnings",
                        "failOnWarnings",
                        "fail_on_warning",
                        "failOnWarning"));
    }

    private static Boolean bool(JsonNode node, String snakeCase, String camelCase) {
        JsonNode value = node.path(snakeCase);
        if (value.isMissingNode()) {
            value = node.path(camelCase);
        }
        return value.isMissingNode() || !value.isBoolean() ? null : value.asBoolean();
    }

    private static String policyProfile(JsonNode root) {
        JsonNode snake = root.path("preflight_policy");
        JsonNode camel = root.path("preflightPolicy");
        JsonNode gate = root.path("preflight_gate");
        JsonNode gateCamel = root.path("preflightGate");
        return firstNonBlank(
                nodeText(snake),
                nodeText(snake.path("profile")),
                nodeText(snake.path("policy")),
                nodeText(camel),
                nodeText(camel.path("profile")),
                nodeText(camel.path("policy")),
                nodeText(gate.path("policy")),
                nodeText(gate.path("profile")),
                nodeText(gateCamel.path("policy")),
                nodeText(gateCamel.path("profile")));
    }

    private static Boolean policyBoolean(JsonNode root, String... names) {
        Boolean topLevel = firstBoolean(root, names);
        if (topLevel != null) {
            return topLevel;
        }
        Boolean policySnake = firstBoolean(root.path("preflight_policy"), names);
        if (policySnake != null) {
            return policySnake;
        }
        Boolean policyCamel = firstBoolean(root.path("preflightPolicy"), names);
        if (policyCamel != null) {
            return policyCamel;
        }
        Boolean gateSnake = firstBoolean(root.path("preflight_gate"), names);
        if (gateSnake != null) {
            return gateSnake;
        }
        return firstBoolean(root.path("preflightGate"), names);
    }

    private static Boolean firstBoolean(JsonNode root, String... names) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        if (names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = root.path(name);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
        }
        return null;
    }

    private static String nodeText(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
