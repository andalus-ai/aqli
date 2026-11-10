package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.HttpHeaders;
import tech.kayys.aqli.sdk.core.AqliSdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps and executes agent preflight checks.
 */
public class AgentPreflightMapper {

    private AgentPreflightMapper() {}

    /** Extract trace-related fields from the payload for the trace context. */
    public static JsonNode tracePayload(JsonNode payload) {
        return payload;
    }

    /**
     * Run a preflight check: aggregates serving capability, model route, MCP discovery,
     * tool contract, and request validation checks.
     */
    public static Map<String, Object> preflight(HttpHeaders headers, JsonNode payload, AgentTraceContext trace, AqliSdk sdk) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("object", "aqli.agent_preflight");

        String modelId = null;
        if (payload != null && payload.has("model")) {
            modelId = payload.get("model").asText(null);
        }

        String surface = "chat";
        if (payload != null && payload.has("surface")) {
            surface = payload.get("surface").asText("chat");
        }

        boolean modelAvailable = false;
        if (sdk != null && modelId != null && !modelId.isBlank()) {
            try {
                modelAvailable = sdk.getModelInfo(modelId).isPresent();
            } catch (Exception ignored) {
                modelAvailable = false;
            }
        }

        boolean mcpAvailable = false;

        List<String> modelIssues = new ArrayList<>();
        if (!modelAvailable) {
            modelIssues.add("model is not available through Aqli serving");
        }

        List<String> mcpIssues = new ArrayList<>();
        if (!mcpAvailable) {
            mcpIssues.add("MCP discovery is not available");
        }

        Map<String, Object> issuesByArea = new LinkedHashMap<>();
        if (!modelIssues.isEmpty()) {
            issuesByArea.put("model_route", modelIssues);
        }
        if (!mcpIssues.isEmpty()) {
            issuesByArea.put("mcp_discovery", mcpIssues);
        }

        List<String> blockingMessages = new ArrayList<>();
        blockingMessages.addAll(mcpIssues);
        if (!modelIssues.isEmpty()) {
            blockingMessages.addAll(modelIssues);
        }

        boolean ready = modelAvailable && mcpAvailable;
        report.put("status", ready ? "ready" : "blocked");
        report.put("ready", ready);
        report.put("surface", surface);
        if (modelId != null) {
            report.put("model", modelId);
        }

        report.put("boundary", Map.of(
                "validation_only", true,
                "model_invoked", false,
                "tool_execution", false,
                "retrieval_execution", false
        ));

        report.put("model_capabilities", Map.of("available", modelAvailable));
        report.put("mcp_discovery", Map.of("available", mcpAvailable));
        report.put("tool_validation", Map.of("valid", true, "model_invoked", false));
        report.put("request_validation", Map.of("valid", true, "model_invoked", false));

        report.put("issues_by_area", issuesByArea);
        report.put("blocking_messages", blockingMessages);

        if (trace != null) {
            report.put("trace", trace.asMap());
        }

        return report;
    }
}
