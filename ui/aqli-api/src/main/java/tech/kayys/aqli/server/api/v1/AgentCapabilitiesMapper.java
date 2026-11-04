package tech.kayys.aqli.server.api.v1;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps agent-facing service capabilities.
 */
public class AgentCapabilitiesMapper {

    private AgentCapabilitiesMapper() {}

    public static Map<String, Object> capabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("service_role", "inference_serving_engine");
        caps.put("compatibility", List.of(
                "openai_chat_completions",
                "openai_chat_streaming",
                "openai_responses",
                "openai_responses_streaming",
                "openai_models",
                "model_capability_matrix",
                "agent_contract",
                "agent_preflight",
                "agent_request_validation",
                "agent_tool_contract_validation",
                "request_trace_context",
                "stream_options",
                "stream_usage_reporting",
                "stream_trace_metadata",
                "mcp_registry",
                "mcp_tool_discovery",
                "mcp_tool_definitions"
        ));
        caps.put("endpoints", Map.ofEntries(
                Map.entry("openai_chat_completions", "/v1/chat/completions"),
                Map.entry("openai_chat_streaming", "/v1/chat/completions"),
                Map.entry("openai_responses", "/v1/responses"),
                Map.entry("openai_responses_streaming", "/v1/responses"),
                Map.entry("openai_models", "/v1/models?compat=openai"),
                Map.entry("model_capabilities", "/v1/models/{id}/capabilities"),
                Map.entry("agent_contract", "/v1/agent/contract"),
                Map.entry("agent_preflight", "/v1/agent/preflight"),
                Map.entry("agent_validation", "/v1/agent/validate"),
                Map.entry("agent_tool_validation", "/v1/agent/tools/validate"),
                Map.entry("mcp_servers", "/v1/mcp/servers"),
                Map.entry("mcp_tools", "/v1/mcp/tools"),
                Map.entry("mcp_server_tools", "/v1/mcp/servers/{name}/tools")
        ));
        caps.put("auth", Map.of(
                "authorization_header", "Bearer token"
        ));
        caps.put("traceability", Map.of(
                "request_id_header", "X-Aqli-Request-Id",
                "metadata_key", "aqli_trace"
        ));
        return caps;
    }
}
