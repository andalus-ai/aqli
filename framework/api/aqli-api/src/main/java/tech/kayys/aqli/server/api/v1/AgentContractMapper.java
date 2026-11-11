package tech.kayys.aqli.server.api.v1;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the canonical agent contract metadata and schema definitions.
 */
public class AgentContractMapper {

    private AgentContractMapper() {}

    public static Map<String, Object> contract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("object", "aqli.agent_contract");
        contract.put("version", "v1");
        contract.put("contract_version", "v1");
        contract.put("supported_contract_versions", List.of("v1"));
        contract.put("service_role", "inference_serving_engine");
        contract.put("compatibility", List.of(
                "openai_chat_completions",
                "openai_chat_streaming",
                "openai_responses",
                "openai_responses_streaming",
                "openai_embeddings",
                "model_capability_matrix",
                "agent_contract",
                "agent_feature_negotiation",
                "agent_preflight",
                "mcp_tool_discovery",
                "rag_context"
        ));

        Map<String, Object> featureNegotiation = new LinkedHashMap<>();
        featureNegotiation.put("mode", "feature_flags");
        featureNegotiation.put("feature_namespace", "aqli.agent.compatibility");
        featureNegotiation.put("contract_version", "v1");
        featureNegotiation.put("supported_contract_versions", List.of("v1"));
        featureNegotiation.put("default_feature_profile", "agent_serving");
        featureNegotiation.put("supported_feature_profiles", List.of(
                "agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"
        ));
        featureNegotiation.put("feature_profiles", List.of(
                Map.of("name", "agent_serving"),
                Map.of("name", "chat_agent"),
                Map.of("name", "responses_agent"),
                Map.of("name", "embedding_rag"),
                Map.of("name", "mcp_tools")
        ));
        featureNegotiation.put("required_features", List.of(
                "agent_feature_negotiation",
                "openai_chat_completions",
                "openai_responses",
                "openai_embeddings",
                "agent_contract",
                "agent_preflight",
                "mcp_tool_discovery",
                "rag_context"
        ));
        featureNegotiation.put("all_features", List.of(
                "agent_feature_negotiation",
                "openai_chat_completions",
                "openai_chat_streaming",
                "openai_responses",
                "openai_responses_streaming",
                "openai_embeddings",
                "agent_contract",
                "agent_preflight",
                "mcp_tool_discovery",
                "rag_context"
        ));
        featureNegotiation.put("unknown_feature_policy", "ignore_for_forward_compatibility");
        contract.put("feature_negotiation", featureNegotiation);

        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("tool_execution", false);
        boundary.put("retrieval_execution", false);
        boundary.put("aqli_owns", List.of(
                "model_serving",
                "provider_routing",
                "system_prompt_mapping",
                "tool_schema_ingestion",
                "tool_contract_validation",
                "mcp_registry_discovery",
                "embedding_generation",
                "rag_context_injection"
        ));
        boundary.put("agent_orchestrator_owns", List.of(
                "planning",
                "memory_policy",
                "retrieval_policy",
                "vector_store_ownership",
                "tool_authorization",
                "tool_execution",
                "tool_result_loop",
                "workflow_state"
        ));
        contract.put("boundary", boundary);

        Map<String, Object> traceability = new LinkedHashMap<>();
        traceability.put("propagates_to_inference_request", true);
        traceability.put("response_metadata_key", "aqli_trace");
        traceability.put("response_headers", List.of(
                "X-Aqli-Request-Id",
                "X-Aqli-Trace-Id",
                "X-Aqli-Session-Id",
                "X-Aqli-User-Id"
        ));
        contract.put("traceability", traceability);

        Map<String, Object> streaming = new LinkedHashMap<>();
        streaming.put("done_sentinel", "[DONE]");
        streaming.put("chat_completions_events", List.of("chat.completion.chunk"));
        streaming.put("responses_events", List.of(
                "response.completed",
                "response.output_item.added",
                "response.output_item.done",
                "response.content_part.added",
                "response.content_part.done",
                "response.text.delta",
                "response.text.done"
        ));
        streaming.put("stream_options", Map.of(
                "include_usage", "Emit usage on final events when provider usage is available.",
                "include_trace", "Emit trace metadata",
                "include_stream_metadata", "Emit stream metadata"
        ));
        contract.put("streaming", streaming);

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("chat_completions", Map.of("method", "POST", "path", "/v1/chat/completions"));
        endpoints.put("responses", Map.of("method", "POST", "path", "/v1/responses"));
        endpoints.put("embeddings", Map.of("method", "POST", "path", "/v1/embeddings"));
        endpoints.put("models", Map.of("method", "GET", "path", "/v1/models"));
        endpoints.put("model_capabilities", Map.of("method", "GET", "path", "/v1/models/{id}/capabilities"));
        endpoints.put("agent_contract", Map.of("method", "GET", "path", "/v1/agent/contract"));
        endpoints.put("agent_readiness_issues", Map.of("method", "GET", "path", "/v1/agent/readiness/issues"));
        endpoints.put("agent_preflight", Map.of("method", "POST", "path", "/v1/agent/preflight"));
        endpoints.put("agent_validation", Map.of("method", "POST", "path", "/v1/agent/validate?surface={surface}"));
        endpoints.put("agent_tool_validation", Map.of("method", "POST", "path", "/v1/agent/tools/validate"));
        endpoints.put("mcp_tools", Map.of("method", "GET", "path", "/v1/mcp/tools?compat=openai"));
        endpoints.put("health", Map.of("method", "GET", "path", "/q/health"));
        contract.put("endpoints", endpoints);

        Map<String, Object> toolDefinitionsInvalid = Map.of(
                "code", "TOOL_DEFINITIONS_INVALID",
                "area", "tool_validation",
                "default_severity", "blocking",
                "summary", "Tool definitions are invalid.",
                "remediation", "Fix the OpenAI-compatible tool schema before serving the route."
        );
        Map<String, Object> agentRequestInvalid = Map.of(
                "code", "AGENT_REQUEST_INVALID",
                "area", "request_validation",
                "default_severity", "blocking",
                "summary", "Agent request is invalid.",
                "remediation", "Fix the request parameters before dispatching."
        );

        contract.put("readiness_issue_catalog", List.of(toolDefinitionsInvalid, agentRequestInvalid));
        contract.put("readiness_issue_catalog_by_code", Map.of(
                "TOOL_DEFINITIONS_INVALID", toolDefinitionsInvalid,
                "AGENT_REQUEST_INVALID", agentRequestInvalid
        ));

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("agent_preflight_request", Map.of(
                "type", "object",
                "properties", Map.of(
                        "feature_profile", Map.of("type", "string"),
                        "required_contract_version", Map.of("type", "string"),
                        "required_features", Map.of("type", "array"),
                        "optional_features", Map.of("type", "array")
                )
        ));

        schemas.put("stream_options", Map.of(
                "type", "object",
                "properties", Map.of(
                        "include_usage", Map.of("type", "boolean"),
                        "include_trace", Map.of("type", "boolean"),
                        "include_stream_metadata", Map.of("type", "boolean")
                )
        ));

        schemas.put("chat_completions_request", Map.of(
                "type", "object",
                "required", List.of("model", "messages"),
                "properties", Map.of(
                        "stream_options", Map.of("$ref", "#/schemas/stream_options")
                )
        ));

        schemas.put("responses_request", Map.of(
                "type", "object",
                "required", List.of("input"),
                "properties", Map.of(
                        "stream_options", Map.of("$ref", "#/schemas/stream_options")
                )
        ));

        schemas.put("rag_context", Map.of(
                "type", "object",
                "accepted_aliases", List.of("context_documents", "retrieval_context"),
                "vector_store_owned_by", "agent_orchestrator",
                "retrieval_policy_owned_by", "agent_orchestrator"
        ));

        schemas.put("openai_tool_definition", Map.of(
                "type", "object",
                "execution", false,
                "validation_endpoint", "/v1/agent/tools/validate"
        ));

        schemas.put("error_response", Map.of(
                "type", "object",
                "required", List.of("error")
        ));

        schemas.put("agent_validation_response", Map.of(
                "type", "object",
                "required", List.of("normalized", "trace", "object", "surface", "valid", "model_invoked", "boundary")
        ));

        schemas.put("agent_readiness_issue_catalog_entry", Map.of(
                "type", "object",
                "required", List.of("code", "area", "default_severity", "summary", "remediation"),
                "properties", Map.of(
                        "code", Map.of("type", "string"),
                        "area", Map.of("type", "string"),
                        "default_severity", Map.of("type", "string"),
                        "summary", Map.of("type", "string"),
                        "remediation", Map.of("type", "string")
                )
        ));

        schemas.put("agent_readiness_issue_catalog_response", Map.of(
                "type", "object",
                "required", List.of("items", "by_code", "by_area"),
                "properties", Map.of(
                        "boundary", Map.of("type", "object"),
                        "count", Map.of("type", "integer"),
                        "items", Map.of("type", "array", "items", Map.of("$ref", "#/schemas/agent_readiness_issue_catalog_entry"))
                )
        ));

        Map<String, Object> preflightCheckResult = Map.of(
                "type", "object",
                "required", List.of(
                        "status", "ready", "requested", "blocking_messages", "warning_messages",
                        "blocking_codes", "warning_codes", "issue_hints", "remediation_plan",
                        "blocking_remediation_plan", "warning_remediation_plan", "remediation_plan_by_code"
                ),
                "properties", Map.ofEntries(
                        Map.entry("status", Map.of("type", "string")),
                        Map.entry("ready", Map.of("type", "boolean")),
                        Map.entry("requested", Map.of("type", "boolean")),
                        Map.entry("blocking_messages", Map.of("type", "array")),
                        Map.entry("warning_messages", Map.of("type", "array")),
                        Map.entry("blocking_codes", Map.of("type", "array")),
                        Map.entry("warning_codes", Map.of("type", "array")),
                        Map.entry("issue_hints", Map.of("type", "array")),
                        Map.entry("remediation_plan", Map.of("type", "array")),
                        Map.entry("blocking_remediation_plan", Map.of("type", "array")),
                        Map.entry("warning_remediation_plan", Map.of("type", "array")),
                        Map.entry("remediation_plan_by_code", Map.of("type", "object")),
                        Map.entry("details", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "embedding", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "rag", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "vector_store_owned_by", Map.of("type", "string")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        ))
                )
        );
        schemas.put("agent_preflight_check_result", preflightCheckResult);

        Map<String, Object> readinessReportSchema = Map.of(
                "type", "object",
                "required", List.of(
                        "contract_version", "supported_contract_versions", "feature_negotiation",
                        "surface", "boundary", "issues_by_area", "issues", "checked_areas",
                        "issue_codes_by_area", "issue_hints", "issue_hints_by_area",
                        "issue_hints_by_code", "remediation_plan", "blocking_remediation_plan",
                        "warning_remediation_plan", "remediation_plan_by_area",
                        "remediation_plan_by_code", "blocking_codes", "warning_codes"
                ),
                "properties", Map.ofEntries(
                        Map.entry("contract_version", Map.of("type", "string")),
                        Map.entry("supported_contract_versions", Map.of("type", "array")),
                        Map.entry("feature_negotiation", Map.of("type", "object")),
                        Map.entry("checks", Map.of(
                                "type", "object",
                                "additionalProperties", Map.of("$ref", "#/schemas/agent_preflight_check_result")
                        )),
                        Map.entry("checked_areas", Map.of("type", "array")),
                        Map.entry("issues", Map.of("type", "array")),
                        Map.entry("issue_codes_by_area", Map.of("type", "object")),
                        Map.entry("issue_hints", Map.of("type", "array")),
                        Map.entry("issue_hints_by_area", Map.of("type", "object")),
                        Map.entry("issue_hints_by_code", Map.of("type", "object")),
                        Map.entry("remediation_plan", Map.of("type", "array")),
                        Map.entry("blocking_remediation_plan", Map.of("type", "array")),
                        Map.entry("warning_remediation_plan", Map.of("type", "array")),
                        Map.entry("remediation_plan_by_area", Map.of("type", "object")),
                        Map.entry("remediation_plan_by_code", Map.of("type", "object")),
                        Map.entry("blocking_codes", Map.of("type", "array")),
                        Map.entry("warning_codes", Map.of("type", "array"))
                )
        );

        schemas.put("agent_preflight_response", Map.of(
                "type", "object",
                "required", List.of(
                        "contract_version", "supported_contract_versions", "feature_negotiation",
                        "issues_by_area", "boundary", "check_results", "readiness_report",
                        "ready_check_count", "checks", "issues", "checked_areas",
                        "issue_codes_by_area", "issue_hints", "issue_hints_by_area",
                        "issue_hints_by_code", "remediation_plan", "blocking_remediation_plan",
                        "warning_remediation_plan", "remediation_plan_by_area",
                        "remediation_plan_by_code", "blocking_codes", "warning_codes"
                ),
                "properties", Map.ofEntries(
                        Map.entry("contract_version", Map.of("type", "string")),
                        Map.entry("supported_contract_versions", Map.of("type", "array")),
                        Map.entry("feature_negotiation", Map.of("type", "object")),
                        Map.entry("checked_areas", Map.of("type", "array")),
                        Map.entry("issue_codes_by_area", Map.of("type", "object")),
                        Map.entry("issue_hints", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("remediation", "summary"),
                                        "properties", Map.of(
                                                "default_severity", Map.of("type", "string"),
                                                "remediation", Map.of("type", "string"),
                                                "summary", Map.of("type", "string")
                                        )
                                )
                        )),
                        Map.entry("issue_hints_by_area", Map.of("type", "object")),
                        Map.entry("issue_hints_by_code", Map.of("type", "object")),
                        Map.entry("remediation_plan", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("messages", "remediation"),
                                        "properties", Map.of(
                                                "code", Map.of("type", "string"),
                                                "messages", Map.of("type", "array"),
                                                "remediation", Map.of("type", "string")
                                        )
                                )
                        )),
                        Map.entry("blocking_remediation_plan", Map.of("type", "array")),
                        Map.entry("warning_remediation_plan", Map.of("type", "array")),
                        Map.entry("remediation_plan_by_area", Map.of("type", "object")),
                        Map.entry("remediation_plan_by_code", Map.of("type", "object")),
                        Map.entry("blocking_codes", Map.of("type", "array")),
                        Map.entry("warning_codes", Map.of("type", "array")),
                        Map.entry("check_results", Map.of(
                                "type", "object",
                                "additionalProperties", Map.of("$ref", "#/schemas/agent_preflight_check_result")
                        )),
                        Map.entry("issues", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("code"),
                                        "properties", Map.of("code", Map.of("type", "string"))
                                )
                        )),
                        Map.entry("readiness_report", readinessReportSchema)
                )
        ));

        schemas.put("tool_contract_validation_response", Map.of(
                "type", "object",
                "required", List.of("warnings", "object", "valid", "model_invoked", "tool_count", "normalized", "boundary")
        ));

        contract.put("schemas", schemas);
        return contract;
    }
}
