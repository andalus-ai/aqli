package tech.kayys.aqli.server.api.v1;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContractMapperTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishesStableAgentIntegrationContract() {
        Map<String, Object> contract = AgentContractMapper.contract();

        assertEquals("aqli.agent_contract", contract.get("object"));
        assertEquals("v1", contract.get("version"));
        assertEquals("v1", contract.get("contract_version"));
        assertEquals(List.of("v1"), contract.get("supported_contract_versions"));
        assertEquals("inference_serving_engine", contract.get("service_role"));
        assertTrue(((List<String>) contract.get("compatibility")).contains("agent_feature_negotiation"));
        Map<String, Object> featureNegotiation = (Map<String, Object>) contract.get("feature_negotiation");
        assertEquals("feature_flags", featureNegotiation.get("mode"));
        assertEquals("aqli.agent.compatibility", featureNegotiation.get("feature_namespace"));
        assertEquals("v1", featureNegotiation.get("contract_version"));
        assertEquals("agent_serving", featureNegotiation.get("default_feature_profile"));
        assertTrue(((List<String>) featureNegotiation.get("supported_feature_profiles")).contains("embedding_rag"));
        assertTrue(((List<Map<String, Object>>) featureNegotiation.get("feature_profiles")).stream()
                .anyMatch(profile -> "mcp_tools".equals(profile.get("name"))));
        assertTrue(((List<String>) featureNegotiation.get("required_features"))
                .contains("agent_feature_negotiation"));
        assertTrue(((List<String>) featureNegotiation.get("all_features")).contains("rag_context"));
        List<Map<String, Object>> readinessIssueCatalog =
                (List<Map<String, Object>>) contract.get("readiness_issue_catalog");
        assertTrue(readinessIssueCatalog.stream()
                .anyMatch(entry -> "TOOL_DEFINITIONS_INVALID".equals(entry.get("code"))
                        && "tool_validation".equals(entry.get("area"))
                        && "blocking".equals(entry.get("default_severity"))
                        && ((String) entry.get("remediation")).contains("tool schema")));
        Map<String, Map<String, Object>> readinessIssueCatalogByCode =
                (Map<String, Map<String, Object>>) contract.get("readiness_issue_catalog_by_code");
        assertEquals(
                "Tool definitions are invalid.",
                readinessIssueCatalogByCode.get("TOOL_DEFINITIONS_INVALID").get("summary"));
        assertTrue(readinessIssueCatalogByCode.containsKey("AGENT_REQUEST_INVALID"));

        Map<String, Object> boundary = (Map<String, Object>) contract.get("boundary");
        assertEquals(false, boundary.get("tool_execution"));
        assertEquals(false, boundary.get("retrieval_execution"));
        assertTrue(((List<String>) boundary.get("aqli_owns")).contains("rag_context_injection"));
        assertTrue(((List<String>) boundary.get("agent_orchestrator_owns")).contains("retrieval_policy"));
        assertTrue(((List<String>) boundary.get("agent_orchestrator_owns")).contains("tool_result_loop"));

        Map<String, Object> traceability = (Map<String, Object>) contract.get("traceability");
        assertEquals(true, traceability.get("propagates_to_inference_request"));
        assertEquals("aqli_trace", traceability.get("response_metadata_key"));
        assertTrue(((List<String>) traceability.get("response_headers")).contains("X-Aqli-Request-Id"));

        Map<String, Object> streaming = (Map<String, Object>) contract.get("streaming");
        assertEquals("[DONE]", streaming.get("done_sentinel"));
        assertTrue(((List<String>) streaming.get("chat_completions_events")).contains("chat.completion.chunk"));
        assertTrue(((List<String>) streaming.get("responses_events")).contains("response.completed"));
        assertTrue(((Map<String, Object>) streaming.get("stream_options")).containsKey("include_usage"));

        Map<String, Object> endpoints = (Map<String, Object>) contract.get("endpoints");
        assertEquals("/v1/chat/completions", ((Map<String, Object>) endpoints.get("chat_completions")).get("path"));
        assertEquals("/v1/models/{id}/capabilities",
                ((Map<String, Object>) endpoints.get("model_capabilities")).get("path"));
        assertEquals("/v1/agent/contract", ((Map<String, Object>) endpoints.get("agent_contract")).get("path"));
        assertEquals("/v1/agent/readiness/issues",
                ((Map<String, Object>) endpoints.get("agent_readiness_issues")).get("path"));
        assertEquals("/v1/agent/preflight",
                ((Map<String, Object>) endpoints.get("agent_preflight")).get("path"));
        assertEquals("/v1/agent/validate?surface={surface}",
                ((Map<String, Object>) endpoints.get("agent_validation")).get("path"));
        assertEquals("/v1/agent/tools/validate",
                ((Map<String, Object>) endpoints.get("agent_tool_validation")).get("path"));

        Map<String, Object> schemas = (Map<String, Object>) contract.get("schemas");
        Map<String, Object> preflightRequest = (Map<String, Object>) schemas.get("agent_preflight_request");
        Map<String, Object> preflightRequestProperties =
                (Map<String, Object>) preflightRequest.get("properties");
        assertTrue(preflightRequestProperties.containsKey("feature_profile"));
        assertTrue(preflightRequestProperties.containsKey("required_contract_version"));
        assertTrue(preflightRequestProperties.containsKey("required_features"));
        assertTrue(preflightRequestProperties.containsKey("optional_features"));

        Map<String, Object> streamOptions = (Map<String, Object>) schemas.get("stream_options");
        assertTrue(((Map<String, Object>) streamOptions.get("properties")).containsKey("include_stream_metadata"));

        Map<String, Object> chatRequest = (Map<String, Object>) schemas.get("chat_completions_request");
        Map<String, Object> chatProperties = (Map<String, Object>) chatRequest.get("properties");
        assertEquals("#/schemas/stream_options",
                ((Map<String, Object>) chatProperties.get("stream_options")).get("$ref"));

        Map<String, Object> responsesRequest = (Map<String, Object>) schemas.get("responses_request");
        Map<String, Object> responsesProperties = (Map<String, Object>) responsesRequest.get("properties");
        assertEquals("#/schemas/stream_options",
                ((Map<String, Object>) responsesProperties.get("stream_options")).get("$ref"));

        Map<String, Object> ragContext = (Map<String, Object>) schemas.get("rag_context");
        assertTrue(((List<String>) ragContext.get("accepted_aliases")).contains("context_documents"));
        assertEquals("agent_orchestrator", ragContext.get("vector_store_owned_by"));

        Map<String, Object> tool = (Map<String, Object>) schemas.get("openai_tool_definition");
        assertEquals(false, tool.get("execution"));
        assertEquals("/v1/agent/tools/validate", tool.get("validation_endpoint"));

        Map<String, Object> error = (Map<String, Object>) schemas.get("error_response");
        assertTrue(((List<String>) error.get("required")).contains("error"));

        Map<String, Object> validation = (Map<String, Object>) schemas.get("agent_validation_response");
        assertTrue(((List<String>) validation.get("required")).contains("normalized"));
        assertTrue(((List<String>) validation.get("required")).contains("trace"));

        Map<String, Object> issueCatalog = (Map<String, Object>) schemas.get("agent_readiness_issue_catalog_response");
        assertTrue(((List<String>) issueCatalog.get("required")).contains("items"));
        assertTrue(((List<String>) issueCatalog.get("required")).contains("by_code"));
        assertTrue(((List<String>) issueCatalog.get("required")).contains("by_area"));
        Map<String, Object> issueCatalogProperties = (Map<String, Object>) issueCatalog.get("properties");
        assertTrue(issueCatalogProperties.containsKey("boundary"));
        assertTrue(issueCatalogProperties.containsKey("count"));
        assertEquals("#/schemas/agent_readiness_issue_catalog_entry",
                ((Map<String, Object>) ((Map<String, Object>) issueCatalogProperties.get("items")).get("items"))
                        .get("$ref"));

        Map<String, Object> preflight = (Map<String, Object>) schemas.get("agent_preflight_response");
        assertTrue(((List<String>) preflight.get("required")).contains("contract_version"));
        assertTrue(((List<String>) preflight.get("required")).contains("supported_contract_versions"));
        assertTrue(((List<String>) preflight.get("required")).contains("feature_negotiation"));
        assertTrue(((List<String>) preflight.get("required")).contains("issues_by_area"));
        assertTrue(((List<String>) preflight.get("required")).contains("boundary"));
        assertTrue(((List<String>) preflight.get("required")).contains("check_results"));
        assertTrue(((List<String>) preflight.get("required")).contains("readiness_report"));
        assertTrue(((List<String>) preflight.get("required")).contains("ready_check_count"));
        assertTrue(((List<String>) preflight.get("required")).contains("checks"));
        assertTrue(((List<String>) preflight.get("required")).contains("issues"));
        assertTrue(((List<String>) preflight.get("required")).contains("checked_areas"));
        assertTrue(((List<String>) preflight.get("required")).contains("issue_codes_by_area"));
        assertTrue(((List<String>) preflight.get("required")).contains("issue_hints"));
        assertTrue(((List<String>) preflight.get("required")).contains("issue_hints_by_area"));
        assertTrue(((List<String>) preflight.get("required")).contains("issue_hints_by_code"));
        assertTrue(((List<String>) preflight.get("required")).contains("remediation_plan"));
        assertTrue(((List<String>) preflight.get("required")).contains("blocking_remediation_plan"));
        assertTrue(((List<String>) preflight.get("required")).contains("warning_remediation_plan"));
        assertTrue(((List<String>) preflight.get("required")).contains("remediation_plan_by_area"));
        assertTrue(((List<String>) preflight.get("required")).contains("remediation_plan_by_code"));
        assertTrue(((List<String>) preflight.get("required")).contains("blocking_codes"));
        assertTrue(((List<String>) preflight.get("required")).contains("warning_codes"));
        Map<String, Object> preflightProperties = (Map<String, Object>) preflight.get("properties");
        assertTrue(preflightProperties.containsKey("contract_version"));
        assertTrue(preflightProperties.containsKey("supported_contract_versions"));
        assertTrue(preflightProperties.containsKey("feature_negotiation"));
        assertTrue(preflightProperties.containsKey("checked_areas"));
        assertTrue(preflightProperties.containsKey("issue_codes_by_area"));
        assertTrue(preflightProperties.containsKey("issue_hints"));
        assertTrue(preflightProperties.containsKey("issue_hints_by_area"));
        assertTrue(preflightProperties.containsKey("issue_hints_by_code"));
        assertTrue(preflightProperties.containsKey("remediation_plan"));
        assertTrue(preflightProperties.containsKey("blocking_remediation_plan"));
        assertTrue(preflightProperties.containsKey("warning_remediation_plan"));
        assertTrue(preflightProperties.containsKey("remediation_plan_by_area"));
        assertTrue(preflightProperties.containsKey("remediation_plan_by_code"));
        assertTrue(preflightProperties.containsKey("blocking_codes"));
        assertTrue(preflightProperties.containsKey("warning_codes"));
        Map<String, Object> checkResults = (Map<String, Object>) preflightProperties.get("check_results");
        assertEquals("object", checkResults.get("type"));
        Map<String, Object> checkSchemaRef = (Map<String, Object>) checkResults.get("additionalProperties");
        assertEquals("#/schemas/agent_preflight_check_result", checkSchemaRef.get("$ref"));
        Map<String, Object> checkSchema = (Map<String, Object>) schemas.get("agent_preflight_check_result");
        Map<String, Object> checkProperties = (Map<String, Object>) checkSchema.get("properties");
        assertTrue(((List<String>) checkSchema.get("required")).contains("status"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("ready"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("requested"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("blocking_codes"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("warning_codes"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("issue_hints"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("remediation_plan"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("blocking_remediation_plan"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("warning_remediation_plan"));
        assertTrue(((List<String>) checkSchema.get("required")).contains("remediation_plan_by_code"));
        assertTrue(checkProperties.containsKey("requested"));
        assertTrue(checkProperties.containsKey("blocking_messages"));
        assertTrue(checkProperties.containsKey("blocking_codes"));
        assertTrue(checkProperties.containsKey("warning_codes"));
        assertTrue(checkProperties.containsKey("issue_hints"));
        assertTrue(checkProperties.containsKey("remediation_plan"));
        assertTrue(checkProperties.containsKey("blocking_remediation_plan"));
        assertTrue(checkProperties.containsKey("warning_remediation_plan"));
        assertTrue(checkProperties.containsKey("remediation_plan_by_code"));

        Map<String, Object> issuesSchema = (Map<String, Object>) preflightProperties.get("issues");
        Map<String, Object> issueItems = (Map<String, Object>) issuesSchema.get("items");
        Map<String, Object> issueProperties = (Map<String, Object>) issueItems.get("properties");
        assertTrue(((List<String>) issueItems.get("required")).contains("code"));
        assertTrue(issueProperties.containsKey("code"));
        Map<String, Object> issueHintsSchema = (Map<String, Object>) preflightProperties.get("issue_hints");
        Map<String, Object> issueHintItems = (Map<String, Object>) issueHintsSchema.get("items");
        Map<String, Object> issueHintProperties = (Map<String, Object>) issueHintItems.get("properties");
        assertTrue(((List<String>) issueHintItems.get("required")).contains("remediation"));
        assertTrue(((List<String>) issueHintItems.get("required")).contains("summary"));
        assertTrue(issueHintProperties.containsKey("default_severity"));
        assertTrue(issueHintProperties.containsKey("remediation"));
        Map<String, Object> remediationSchema = (Map<String, Object>) preflightProperties.get("remediation_plan");
        Map<String, Object> remediationItems = (Map<String, Object>) remediationSchema.get("items");
        Map<String, Object> remediationProperties = (Map<String, Object>) remediationItems.get("properties");
        assertTrue(((List<String>) remediationItems.get("required")).contains("messages"));
        assertTrue(((List<String>) remediationItems.get("required")).contains("remediation"));
        assertTrue(remediationProperties.containsKey("code"));
        assertTrue(remediationProperties.containsKey("messages"));

        Map<String, Object> readinessReport = (Map<String, Object>) preflightProperties.get("readiness_report");
        assertEquals("object", readinessReport.get("type"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("contract_version"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("supported_contract_versions"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("feature_negotiation"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("surface"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("boundary"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issues_by_area"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issues"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("checked_areas"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issue_codes_by_area"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issue_hints"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issue_hints_by_area"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("issue_hints_by_code"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("remediation_plan"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("blocking_remediation_plan"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("warning_remediation_plan"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("remediation_plan_by_area"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("remediation_plan_by_code"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("blocking_codes"));
        assertTrue(((List<String>) readinessReport.get("required")).contains("warning_codes"));
        Map<String, Object> readinessProperties = (Map<String, Object>) readinessReport.get("properties");
        assertTrue(readinessProperties.containsKey("contract_version"));
        assertTrue(readinessProperties.containsKey("supported_contract_versions"));
        assertTrue(readinessProperties.containsKey("feature_negotiation"));
        Map<String, Object> readinessChecks = (Map<String, Object>) readinessProperties.get("checks");
        assertEquals(checkSchemaRef, readinessChecks.get("additionalProperties"));
        assertTrue(readinessProperties.containsKey("checked_areas"));
        assertTrue(readinessProperties.containsKey("issues"));
        assertTrue(readinessProperties.containsKey("issue_codes_by_area"));
        assertTrue(readinessProperties.containsKey("issue_hints"));
        assertTrue(readinessProperties.containsKey("issue_hints_by_area"));
        assertTrue(readinessProperties.containsKey("issue_hints_by_code"));
        assertTrue(readinessProperties.containsKey("remediation_plan"));
        assertTrue(readinessProperties.containsKey("blocking_remediation_plan"));
        assertTrue(readinessProperties.containsKey("warning_remediation_plan"));
        assertTrue(readinessProperties.containsKey("remediation_plan_by_area"));
        assertTrue(readinessProperties.containsKey("remediation_plan_by_code"));
        assertTrue(readinessProperties.containsKey("blocking_codes"));
        assertTrue(readinessProperties.containsKey("warning_codes"));

        Map<String, Object> toolValidation = (Map<String, Object>) schemas.get("tool_contract_validation_response");
        assertTrue(((List<String>) toolValidation.get("required")).contains("warnings"));
    }
}
